package com.petrolpump.discount.service;

import com.petrolpump.discount.domain.*;
import com.petrolpump.discount.repo.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
public class ClaimService {
    private final AuthService auth;
    private final AppUserRepository users;
    private final VehicleLinkRepository vehicles;
    private final PumpRepository pumps;
    private final BillClaimRepository claims;
    private final PhoneBlacklistRepository blacklist;
    private final AdminAlertRepository alerts;
    private final GeminiBillOcrService ocr;
    private final Path uploadDir;

    public ClaimService(AuthService auth, AppUserRepository users, VehicleLinkRepository vehicles, PumpRepository pumps,
                        BillClaimRepository claims, PhoneBlacklistRepository blacklist,
                        AdminAlertRepository alerts, GeminiBillOcrService ocr,
                        @Value("${app.upload-dir:./uploads}") String uploadDir) throws Exception {
        this.auth = auth;
        this.users = users;
        this.vehicles = vehicles;
        this.pumps = pumps;
        this.claims = claims;
        this.blacklist = blacklist;
        this.alerts = alerts;
        this.ocr = ocr;
        this.uploadDir = Paths.get(uploadDir).toAbsolutePath().normalize();
        Files.createDirectories(this.uploadDir);
    }

    @Transactional
    public BillClaim upload(String sessionToken, String phone, String vehicleNo, MultipartFile image,
                            Double lat, Double lng) throws Exception {
        if (image == null || image.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bill photo required");
        }

        AppUser user;
        if (sessionToken != null && !sessionToken.isBlank()) {
            user = auth.requireUser(sessionToken);
            phone = user.getPhone();
        } else {
            phone = AuthService.normalizePhone(phone);
            user = users.findByPhone(phone)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Please register first"));
        }

        if (blacklist.existsByPhone(phone)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Phone not eligible to claim");
        }

        String ctype = image.getContentType() == null ? "" : image.getContentType().toLowerCase();
        if (ctype.contains(";")) ctype = ctype.substring(0, ctype.indexOf(';')).trim();
        if (!ctype.isBlank() && !ctype.startsWith("image/")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only image uploads allowed");
        }
        if (image.getSize() > 12L * 1024 * 1024) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Image too large (max 12MB)");
        }

        String normVeh = VehicleNormalizer.normalize(vehicleNo);
        if (!vehicles.existsByUserAndRegNo(user, normVeh)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Vehicle not linked to this account");
        }
        Pump pump = pumps.findAll().stream().findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Pump not configured"));
        // Always read lat/lng/radius from DB so Admin geo edits apply without restart.

        if (lat == null || lng == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Location required");
        }
        double dist = GeoUtil.haversineMeters(pump.getLat(), pump.getLng(), lat, lng);
        if (dist > pump.getRadiusMeters()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Upload only within pump boundary (" + (int) pump.getRadiusMeters() + "m)");
        }

        BillOcrResult extracted = ocr.extract(image);
        String billNo = BillOcrResult.normalizeBillNo(extracted.getBillNo());
        if (extracted.isDuplicate()
                || (billNo != null && billNo.endsWith("-DUPLT"))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Duplicate bill uploaded");
        }

        var candidateKeys = extracted.candidateReceiptKeys();
        if (candidateKeys.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Could not read FCC ID / Trans ID from bill");
        }
        for (String k : candidateKeys) {
            if (claims.existsByReceiptKey(k)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Bill already submitted");
            }
        }
        if (billNo != null && claims.existsByBillNoIgnoreCase(billNo)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Bill already submitted");
        }

        Double volume = extracted.getVolumeLitres();
        if (volume == null || volume <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Could not read volume from bill");
        }

        String billVehicle = extracted.getVehicleNo();
        if (VehicleNormalizer.isBlankOnBill(billVehicle)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Vehicle number missing on bill (NotEntered)");
        }
        if (!VehicleNormalizer.normalize(billVehicle).equals(normVeh)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Vehicle on bill does not match entered vehicle");
        }

        String key = candidateKeys.get(0);
        String filename = key + "_" + System.currentTimeMillis() + ".jpg";
        Path dest = uploadDir.resolve(filename);
        Files.copy(image.getInputStream(), dest, StandardCopyOption.REPLACE_EXISTING);

        BillClaim claim = new BillClaim();
        claim.setUser(user);
        claim.setPump(pump);
        claim.setReceiptKey(key);
        claim.setFccId(BillOcrResult.normalizeReceiptDigits(extracted.getFccId()));
        claim.setTransId(BillOcrResult.normalizeReceiptDigits(extracted.getTransId()));
        claim.setVehicleNo(normVeh);
        claim.setVolumeLitres(volume);
        claim.setSaleAmount(extracted.getSaleAmount());
        claim.setBillNo(billNo);
        claim.setImagePath(dest.toString());
        claim.setClaimLat(lat);
        claim.setClaimLng(lng);
        claim.setDistanceMeters(dist);
        claim.setBillTime(Instant.now());
        claim.setStatus(ClaimStatus.QUEUED);

        BillClaim saved;
        try {
            saved = claims.saveAndFlush(claim);
        } catch (DataIntegrityViolationException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Bill already submitted");
        }

        Instant since = Instant.now().minus(24, ChronoUnit.HOURS);
        long recent = claims.countByUserAndCreatedAtAfter(user, since);
        if (recent > 1) {
            AdminAlert a = new AdminAlert();
            a.setType("REPEAT_UPLOAD_24H");
            a.setPhone(phone);
            a.setMessage("Phone " + phone + " uploaded " + recent + " bills within 24h. Latest receipt " + key);
            alerts.save(a);
        }
        return saved;
    }
}
