package com.petrolpump.discount.web;

import com.petrolpump.discount.domain.BillClaim;
import com.petrolpump.discount.repo.BillClaimRepository;
import com.petrolpump.discount.service.AuthService;
import com.petrolpump.discount.service.ClaimService;
import com.petrolpump.discount.service.RateLimitService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/claims")
public class ClaimController {
    private final ClaimService claims;
    private final AuthService auth;
    private final BillClaimRepository claimRepo;
    private final RateLimitService rateLimit;

    public ClaimController(ClaimService claims, AuthService auth, BillClaimRepository claimRepo,
                           RateLimitService rateLimit) {
        this.claims = claims;
        this.auth = auth;
        this.claimRepo = claimRepo;
        this.rateLimit = rateLimit;
    }

    @GetMapping("/mine")
    public List<Map<String, Object>> mine(@RequestHeader("X-Session-Token") String token) {
        var user = auth.requireUser(token);
        return claimRepo.findByUserOrderByCreatedAtDesc(user).stream()
                .map(this::dto)
                .collect(Collectors.toList());
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> upload(
            @RequestHeader(value = "X-Session-Token", required = false) String sessionToken,
            @RequestParam(required = false) String phone,
            @RequestParam String vehicleNo,
            @RequestParam MultipartFile image,
            @RequestParam double lat,
            @RequestParam double lng
    ) throws Exception {
        String throttleKey = sessionToken != null && !sessionToken.isBlank()
                ? "upload:" + sessionToken
                : "upload:" + (phone == null ? "anon" : phone);
        rateLimit.check(throttleKey, 2);
        var c = claims.upload(sessionToken, phone, vehicleNo, image, lat, lng);
        return Map.of(
                "id", c.getId(),
                "status", c.getStatus().name(),
                "receiptKey", c.getReceiptKey(),
                "volumeLitres", c.getVolumeLitres(),
                "message", "Submitted for verification. Coins after daily confirmation."
        );
    }

    private Map<String, Object> dto(BillClaim c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", c.getId());
        m.put("createdAt", c.getCreatedAt().toString());
        m.put("billTime", c.getBillTime() == null ? null : c.getBillTime().toString());
        m.put("vehicleNo", c.getVehicleNo());
        m.put("billNo", c.getBillNo() == null ? "" : c.getBillNo());
        m.put("receiptKey", c.getReceiptKey());
        m.put("fccId", c.getFccId() == null ? "" : c.getFccId());
        m.put("transId", c.getTransId() == null ? "" : c.getTransId());
        m.put("volumeLitres", c.getVolumeLitres());
        m.put("saleAmount", c.getSaleAmount());
        m.put("status", c.getStatus().name());
        m.put("rejectReason", c.getRejectReason() == null ? "" : c.getRejectReason());
        m.put("coinsCredited", c.getCoinsCredited());
        m.put("decidedAt", c.getDecidedAt() == null ? null : c.getDecidedAt().toString());
        return m;
    }
}
