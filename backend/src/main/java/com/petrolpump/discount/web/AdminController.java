package com.petrolpump.discount.web;

import com.petrolpump.discount.domain.*;
import com.petrolpump.discount.repo.*;
import com.petrolpump.discount.service.AuthService;
import com.petrolpump.discount.service.BusinessDay;
import com.petrolpump.discount.service.PdfMatchService;
import com.petrolpump.discount.service.VehicleNormalizer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin")
public class AdminController {
    private final AuthService auth;
    private final PdfMatchService pdfMatch;
    private final LoyaltyConfigRepository configs;
    private final PhoneBlacklistRepository blacklist;
    private final RejectIdRepository rejectIds;
    private final BillClaimRepository claims;
    private final RedeemTransactionRepository redeems;
    private final AdminAlertRepository alerts;
    private final PumpRepository pumps;
    private final AppUserRepository users;
    private final VehicleLinkRepository vehicles;
    private final Path uploadDir;

    public AdminController(AuthService auth, PdfMatchService pdfMatch, LoyaltyConfigRepository configs,
                           PhoneBlacklistRepository blacklist, RejectIdRepository rejectIds,
                           BillClaimRepository claims, RedeemTransactionRepository redeems,
                           AdminAlertRepository alerts, PumpRepository pumps, AppUserRepository users,
                           VehicleLinkRepository vehicles,
                           @Value("${app.upload-dir:./uploads}") String uploadDir) {
        this.auth = auth; this.pdfMatch = pdfMatch; this.configs = configs; this.blacklist = blacklist;
        this.rejectIds = rejectIds; this.claims = claims; this.redeems = redeems; this.alerts = alerts;
        this.pumps = pumps; this.users = users; this.vehicles = vehicles;
        this.uploadDir = Paths.get(uploadDir).toAbsolutePath().normalize();
    }

    private void admin(String token) { auth.requireRole(token, UserRole.ADMIN); }

    @GetMapping("/config")
    public LoyaltyConfig getConfig(@RequestHeader("X-Session-Token") String token) {
        admin(token);
        return configs.findById(1L).orElseThrow();
    }

    @PutMapping("/config")
    public LoyaltyConfig putConfig(@RequestHeader("X-Session-Token") String token, @RequestBody LoyaltyConfig body) {
        admin(token);
        body.setId(1L);
        return configs.save(body);
    }

    @PostMapping("/pdf")
    public Map<String, Object> uploadPdf(@RequestHeader("X-Session-Token") String token,
                                         @RequestParam MultipartFile pdf,
                                         @RequestParam(required = false) String rejectIdsCsv) throws Exception {
        admin(token);
        List<String> extra = rejectIdsCsv == null || rejectIdsCsv.isBlank() ? List.of()
                : Arrays.stream(rejectIdsCsv.split("[,\s]+")).filter(s -> !s.isBlank()).toList();
        return pdfMatch.processPdf(pdf, extra);
    }

    @PostMapping("/blacklist")
    public Map<String, Object> addBlacklist(@RequestHeader("X-Session-Token") String token, @RequestBody Map<String, String> body) {
        admin(token);
        PhoneBlacklist row = new PhoneBlacklist();
        row.setPhone(AuthService.normalizePhone(body.get("phone")));
        row.setReason(body.getOrDefault("reason", ""));
        blacklist.save(row);
        return Map.of("id", row.getId(), "phone", row.getPhone());
    }

    @GetMapping("/blacklist")
    public List<PhoneBlacklist> listBlacklist(@RequestHeader("X-Session-Token") String token) {
        admin(token);
        return blacklist.findAll();
    }

    @GetMapping("/claims")
    public List<Map<String, Object>> claims(@RequestHeader("X-Session-Token") String token,
                                            @RequestParam(required = false) String status) {
        admin(token);
        List<BillClaim> list = status == null ? claims.findAll()
                : claims.findByStatusOrderByCreatedAtAsc(ClaimStatus.valueOf(status));
        return list.stream().map(this::claimDto).collect(Collectors.toList());
    }

    @GetMapping("/claims/{id}/photo")
    public ResponseEntity<Resource> claimPhoto(@RequestHeader("X-Session-Token") String token,
                                               @PathVariable Long id) {
        admin(token);
        BillClaim c = claims.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Claim not found"));
        String stored = c.getImagePath();
        if (stored == null || stored.isBlank()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No photo for this claim");
        }
        Path file = Paths.get(stored).toAbsolutePath().normalize();
        if (!file.startsWith(uploadDir) || !Files.isRegularFile(file)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Photo file missing");
        }
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        MediaType type = name.endsWith(".png") ? MediaType.IMAGE_PNG
                : name.endsWith(".webp") ? MediaType.parseMediaType("image/webp")
                : MediaType.IMAGE_JPEG;
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=300")
                .contentType(type)
                .body(new FileSystemResource(file));
    }

    @GetMapping("/alerts")
    public List<AdminAlert> alerts(@RequestHeader("X-Session-Token") String token) {
        admin(token);
        return alerts.findTop50ByOrderByCreatedAtDesc();
    }

    @DeleteMapping("/alerts/{id}")
    public Map<String, String> clearAlert(@RequestHeader("X-Session-Token") String token, @PathVariable Long id) {
        admin(token);
        if (!alerts.existsById(id)) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.NOT_FOUND, "Alert not found");
        }
        alerts.deleteById(id);
        return Map.of("status", "cleared");
    }

    @GetMapping("/reports/summary")
    public Map<String, Object> summary(@RequestHeader("X-Session-Token") String token) {
        admin(token);
        String day = BusinessDay.of(Instant.now());
        var dayRedeems = redeems.findByBusinessDayOrderByCreatedAtDesc(day);
        long redeemCoins = dayRedeems.stream().mapToLong(RedeemTransaction::getCoins).sum();
        long queued = claims.findByStatusOrderByCreatedAtAsc(ClaimStatus.QUEUED).size();
        return Map.of(
                "businessDay", day,
                "redeemCount", dayRedeems.size(),
                "redeemCoins", redeemCoins,
                "redeemRupees", redeemCoins / 100.0,
                "queuedClaims", queued
        );
    }

    @PutMapping("/pump/geo")
    public Pump updateGeo(@RequestHeader("X-Session-Token") String token, @RequestBody Map<String, Object> body) {
        return updatePump(token, body);
    }

    @GetMapping("/pump")
    public Pump getPump(@RequestHeader("X-Session-Token") String token) {
        admin(token);
        return pumps.findAll().get(0);
    }

    @PutMapping("/pump")
    public Pump updatePump(@RequestHeader("X-Session-Token") String token, @RequestBody Map<String, Object> body) {
        admin(token);
        Pump p = pumps.findAll().get(0);
        if (body.get("lat") != null) p.setLat(Double.parseDouble(body.get("lat").toString()));
        if (body.get("lng") != null) p.setLng(Double.parseDouble(body.get("lng").toString()));
        if (body.get("radiusMeters") != null) p.setRadiusMeters(Double.parseDouble(body.get("radiusMeters").toString()));
        if (body.get("name") != null) p.setName(String.valueOf(body.get("name")).trim());
        if (body.get("address") != null) p.setAddress(String.valueOf(body.get("address")).trim());
        if (body.get("contactName") != null) p.setContactName(String.valueOf(body.get("contactName")).trim());
        if (body.get("contactPhone") != null) {
            String ph = String.valueOf(body.get("contactPhone")).replaceAll("\\D", "");
            if (ph.length() > 10) ph = ph.substring(ph.length() - 10);
            p.setContactPhone(ph);
        }
        if (body.get("mapsUrl") != null) p.setMapsUrl(String.valueOf(body.get("mapsUrl")).trim());
        return pumps.save(p);
    }

    @GetMapping("/users")
    public List<Map<String, Object>> listUsers(@RequestHeader("X-Session-Token") String token) {
        return listStaff(token);
    }

    @GetMapping("/users/staff")
    public List<Map<String, Object>> listStaff(@RequestHeader("X-Session-Token") String token) {
        admin(token);
        return users.findByRoleInOrderByPhoneAsc(List.of(UserRole.ADMIN, UserRole.EMPLOYEE)).stream()
                .map(this::userDto)
                .collect(Collectors.toList());
    }

    @GetMapping("/users/search")
    public List<Map<String, Object>> searchUsers(@RequestHeader("X-Session-Token") String token,
                                                 @RequestParam(required = false) String phone,
                                                 @RequestParam(required = false) String vehicle) {
        admin(token);
        LinkedHashMap<Long, AppUser> found = new LinkedHashMap<>();
        if (phone != null && !phone.isBlank()) {
            users.findByPhone(AuthService.normalizePhone(phone)).ifPresent(u -> found.put(u.getId(), u));
        }
        if (vehicle != null && !vehicle.isBlank()) {
            String reg = VehicleNormalizer.normalize(vehicle);
            for (VehicleLink link : vehicles.findByRegNo(reg)) {
                found.put(link.getUser().getId(), link.getUser());
            }
        }
        if (found.isEmpty() && (phone == null || phone.isBlank()) && (vehicle == null || vehicle.isBlank())) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST, "Provide phone or vehicle");
        }
        return found.values().stream().map(this::userDto).collect(Collectors.toList());
    }

    @PutMapping("/users/role")
    public Map<String, Object> setRole(@RequestHeader("X-Session-Token") String token,
                                       @RequestBody Map<String, String> body) {
        AppUser actor = auth.requireRole(token, UserRole.ADMIN);
        String phone = AuthService.normalizePhone(body.get("phone"));
        UserRole role;
        try {
            role = UserRole.valueOf(body.getOrDefault("role", "").trim().toUpperCase(Locale.ROOT));
        } catch (Exception ex) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST, "Role must be DRIVER, EMPLOYEE, or ADMIN");
        }

        AppUser user = users.findByPhone(phone).orElseGet(() -> {
            AppUser u = new AppUser();
            u.setPhone(phone);
            u.setName(body.getOrDefault("name", "").isBlank() ? phone : body.get("name"));
            return u;
        });

        if (actor.getPhone().equals(phone) && role != UserRole.ADMIN) {
            long otherAdmins = users.findByRoleInOrderByPhoneAsc(List.of(UserRole.ADMIN)).stream()
                    .filter(u -> !u.getPhone().equals(phone))
                    .count();
            if (otherAdmins == 0) {
                throw new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.BAD_REQUEST,
                        "Cannot remove the last ADMIN — promote someone else first");
            }
        }

        user.setRole(role);
        if (body.get("name") != null && !body.get("name").isBlank()
                && (user.getName() == null || user.getName().isBlank())) {
            user.setName(body.get("name"));
        }
        users.save(user);
        return Map.of("phone", user.getPhone(), "role", user.getRole().name(),
                "name", user.getName() == null ? "" : user.getName());
    }

    private Map<String, Object> userDto(AppUser u) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", u.getId());
        m.put("phone", u.getPhone());
        m.put("name", u.getName() == null ? "" : u.getName());
        m.put("role", u.getRole().name());
        m.put("walletCoins", u.getWalletCoins());
        return m;
    }

    private Map<String, Object> claimDto(BillClaim c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", c.getId());
        m.put("phone", c.getUser().getPhone());
        m.put("vehicleNo", c.getVehicleNo());
        m.put("receiptKey", c.getReceiptKey());
        m.put("volume", c.getVolumeLitres());
        m.put("status", c.getStatus().name());
        m.put("coinsCredited", c.getCoinsCredited());
        m.put("rejectReason", c.getRejectReason());
        m.put("createdAt", c.getCreatedAt().toString());
        m.put("hasPhoto", c.getImagePath() != null && !c.getImagePath().isBlank());
        return m;
    }
}
