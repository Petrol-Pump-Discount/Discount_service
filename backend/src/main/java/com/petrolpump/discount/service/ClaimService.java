package com.petrolpump.discount.service;

import com.petrolpump.discount.domain.*;
import com.petrolpump.discount.repo.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.nio.file.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class ClaimService {
    private final AppUserRepository users;
    private final VehicleLinkRepository vehicles;
    private final PumpRepository pumps;
    private final BillClaimRepository claims;
    private final PhoneBlacklistRepository blacklist;
    private final AdminAlertRepository alerts;
    private final Path uploadDir;

    public ClaimService(AppUserRepository users, VehicleLinkRepository vehicles, PumpRepository pumps,
                        BillClaimRepository claims, PhoneBlacklistRepository blacklist,
                        AdminAlertRepository alerts, @Value("${app.upload-dir:./uploads}") String uploadDir) throws Exception {
        this.users = users; this.vehicles = vehicles; this.pumps = pumps; this.claims = claims;
        this.blacklist = blacklist; this.alerts = alerts;
        this.uploadDir = Paths.get(uploadDir).toAbsolutePath().normalize();
        Files.createDirectories(this.uploadDir);
    }

    @Transactional
    public BillClaim guestUpload(String phone, String vehicleNo, MultipartFile image,
                                 Double lat, Double lng,
                                 String receiptKey, String billNo, Double volume,
                                 Boolean duplicateFlag) throws Exception {
        phone = AuthService.normalizePhone(phone);
        if (blacklist.existsByPhone(phone)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Phone not eligible to claim");
        }
        AppUser user = users.findByPhone(phone)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Please register first"));
        String normVeh = VehicleNormalizer.normalize(vehicleNo);
        if (!vehicles.existsByUserAndRegNo(user, normVeh)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Vehicle not linked to this account");
        }
        Pump pump = pumps.findAll().stream().findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Pump not configured"));

        if (lat == null || lng == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Location required");
        }
        double dist = GeoUtil.haversineMeters(pump.getLat(), pump.getLng(), lat, lng);
        if (dist > pump.getRadiusMeters()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Upload only within pump boundary (" + (int) pump.getRadiusMeters() + "m)");
        }

        // OCR stub fields supplied by client until Vision is wired; validate rules here
        if (Boolean.TRUE.equals(duplicateFlag) || (billNo != null && billNo.toUpperCase(Locale.ROOT).endsWith("-DUPLT"))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Duplicate bill uploaded");
        }
        if (receiptKey == null || receiptKey.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "FCC ID / Trans ID required");
        }
        String key = receiptKey.replaceAll("\\D", "");
        if (key.length() > 9) key = key.substring(key.length() - 9);
        if (key.length() < 9) key = String.format("%9s", key).replace(' ', '0');
        if (claims.existsByReceiptKey(key)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Bill already submitted");
        }
        if (volume == null || volume <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Volume required");
        }

        String filename = key + "_" + System.currentTimeMillis() + ".jpg";
        Path dest = uploadDir.resolve(filename);
        Files.copy(image.getInputStream(), dest, StandardCopyOption.REPLACE_EXISTING);

        BillClaim claim = new BillClaim();
        claim.setUser(user);
        claim.setPump(pump);
        claim.setReceiptKey(key);
        claim.setVehicleNo(normVeh);
        claim.setVolumeLitres(volume);
        claim.setBillNo(billNo);
        claim.setImagePath(dest.toString());
        claim.setClaimLat(lat);
        claim.setClaimLng(lng);
        claim.setDistanceMeters(dist);
        claim.setBillTime(Instant.now());
        claim.setStatus(ClaimStatus.QUEUED);
        BillClaim saved = claims.save(claim);

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
