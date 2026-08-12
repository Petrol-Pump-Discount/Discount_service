package com.petrolpump.discount.web;

import com.petrolpump.discount.domain.*;
import com.petrolpump.discount.repo.*;
import com.petrolpump.discount.service.AuthService;
import com.petrolpump.discount.service.BusinessDay;
import com.petrolpump.discount.service.PdfMatchService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

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

    public AdminController(AuthService auth, PdfMatchService pdfMatch, LoyaltyConfigRepository configs,
                           PhoneBlacklistRepository blacklist, RejectIdRepository rejectIds,
                           BillClaimRepository claims, RedeemTransactionRepository redeems,
                           AdminAlertRepository alerts, PumpRepository pumps) {
        this.auth = auth; this.pdfMatch = pdfMatch; this.configs = configs; this.blacklist = blacklist;
        this.rejectIds = rejectIds; this.claims = claims; this.redeems = redeems; this.alerts = alerts;
        this.pumps = pumps;
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

    @GetMapping("/alerts")
    public List<AdminAlert> alerts(@RequestHeader("X-Session-Token") String token) {
        admin(token);
        return alerts.findTop50ByOrderByCreatedAtDesc();
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
    public Pump updateGeo(@RequestHeader("X-Session-Token") String token, @RequestBody Map<String, Double> body) {
        admin(token);
        Pump p = pumps.findAll().get(0);
        if (body.get("lat") != null) p.setLat(body.get("lat"));
        if (body.get("lng") != null) p.setLng(body.get("lng"));
        if (body.get("radiusMeters") != null) p.setRadiusMeters(body.get("radiusMeters"));
        return pumps.save(p);
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
        return m;
    }
}
