package com.petrolpump.discount.web;

import com.petrolpump.discount.domain.BillClaim;
import com.petrolpump.discount.domain.WalletAdjustment;
import com.petrolpump.discount.repo.BillClaimRepository;
import com.petrolpump.discount.repo.WalletAdjustmentRepository;
import com.petrolpump.discount.service.AuthService;
import com.petrolpump.discount.service.ClaimService;
import com.petrolpump.discount.service.RateLimitService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;

@RestController
@RequestMapping("/api/claims")
public class ClaimController {
    private static final Logger log = LoggerFactory.getLogger(ClaimController.class);

    private final ClaimService claims;
    private final AuthService auth;
    private final BillClaimRepository claimRepo;
    private final WalletAdjustmentRepository walletAdjustments;
    private final RateLimitService rateLimit;

    public ClaimController(ClaimService claims, AuthService auth, BillClaimRepository claimRepo,
                           WalletAdjustmentRepository walletAdjustments, RateLimitService rateLimit) {
        this.claims = claims;
        this.auth = auth;
        this.claimRepo = claimRepo;
        this.walletAdjustments = walletAdjustments;
        this.rateLimit = rateLimit;
    }

    @GetMapping("/mine")
    public List<Map<String, Object>> mine(@RequestHeader("X-Session-Token") String token) {
        var user = auth.requireUser(token);
        List<Map<String, Object>> out = new ArrayList<>();
        for (BillClaim c : claimRepo.findByUserOrderByCreatedAtDesc(user)) {
            out.add(dto(c));
        }
        for (WalletAdjustment a : walletAdjustments.findByUserOrderByCreatedAtDesc(user)) {
            out.add(walletDto(a));
        }
        out.sort((a, b) -> String.valueOf(b.get("createdAt")).compareTo(String.valueOf(a.get("createdAt"))));
        return out;
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
        long bytes = image == null ? 0 : image.getSize();
        log.info("UPLOAD hit vehicle={} bytes={} lat={} lng={} authed={}",
                vehicleNo, bytes, lat, lng, sessionToken != null && !sessionToken.isBlank());

        String throttleKey = sessionToken != null && !sessionToken.isBlank()
                ? "upload:" + sessionToken
                : "upload:" + (phone == null ? "anon" : phone);
        rateLimit.checkWindow(throttleKey, 5, 10);

        try {
            var c = claims.upload(sessionToken, phone, vehicleNo, image, lat, lng);
            log.info("UPLOAD ok id={} receipt={} status={}", c.getId(), c.getReceiptKey(), c.getStatus());
            return Map.of(
                    "id", c.getId(),
                    "status", c.getStatus().name(),
                    "receiptKey", c.getReceiptKey(),
                    "volumeLitres", c.getVolumeLitres(),
                    "message", "Submitted for verification. Coins after daily confirmation."
            );
        } catch (Exception ex) {
            log.warn("UPLOAD fail vehicle={} err={}", vehicleNo, ex.toString());
            throw ex;
        }
    }

    private Map<String, Object> dto(BillClaim c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("kind", "CLAIM");
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

    private Map<String, Object> walletDto(WalletAdjustment a) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("kind", "ADJUSTMENT");
        m.put("id", a.getId());
        m.put("createdAt", a.getCreatedAt().toString());
        m.put("status", a.getDeltaCoins() >= 0 ? "CREDIT" : "DEBIT");
        m.put("coinsCredited", a.getDeltaCoins());
        m.put("balanceAfter", a.getBalanceAfter());
        m.put("rejectReason", a.getReason());
        m.put("vehicleNo", "");
        m.put("receiptKey", "Wallet adjustment");
        m.put("billNo", "");
        m.put("fccId", "");
        m.put("transId", "");
        m.put("volumeLitres", null);
        return m;
    }
}
