package com.petrolpump.discount.web;

import com.petrolpump.discount.domain.RedeemTransaction;
import com.petrolpump.discount.repo.PumpRepository;
import com.petrolpump.discount.repo.RedeemTransactionRepository;
import com.petrolpump.discount.service.AuthService;
import com.petrolpump.discount.service.RateLimitService;
import com.petrolpump.discount.service.RedeemService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/redeem")
public class RedeemController {
    private final AuthService auth;
    private final RedeemService redeem;
    private final PumpRepository pumps;
    private final RedeemTransactionRepository redeems;
    private final RateLimitService rateLimit;

    public RedeemController(AuthService auth, RedeemService redeem, PumpRepository pumps,
                            RedeemTransactionRepository redeems, RateLimitService rateLimit) {
        this.auth = auth;
        this.redeem = redeem;
        this.pumps = pumps;
        this.redeems = redeems;
        this.rateLimit = rateLimit;
    }

    @GetMapping("/pump/{token}")
    public Map<String, Object> pumpInfo(@PathVariable String token) {
        var p = redeem.requirePumpByToken(token);
        return Map.of("pumpId", p.getId(), "name", p.getName(), "token", p.getRedeemToken());
    }

    @GetMapping("/mine")
    public List<Map<String, Object>> mine(@RequestHeader("X-Session-Token") String session) {
        var user = auth.requireUser(session);
        return redeems.findByUserOrderByCreatedAtDesc(user).stream()
                .map(this::dto)
                .collect(Collectors.toList());
    }

    @PostMapping("/otp/request")
    public Map<String, String> requestPayOtp(@RequestHeader("X-Session-Token") String session) {
        var user = auth.requireUser(session);
        rateLimit.check("redeem-otp:" + user.getPhone(), 30);
        auth.requestOtp(user.getPhone(), AuthService.PURPOSE_REDEEM);
        return Map.of("status", "ok", "message", "OTP sent to " + user.getPhone());
    }

    @PostMapping("/pay")
    public Map<String, Object> pay(@RequestHeader("X-Session-Token") String session,
                                   @RequestBody Map<String, Object> body) {
        var user = auth.requireUser(session);
        rateLimit.check("redeem-pay:" + user.getId(), 5);
        Object otpObj = body.get("otp");
        if (otpObj == null || otpObj.toString().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "OTP required to complete payment");
        }
        auth.verifyActionOtp(user.getPhone(), otpObj.toString().trim(), AuthService.PURPOSE_REDEEM);

        String pumpToken = String.valueOf(body.get("pumpToken"));
        Long coins = body.get("coins") == null ? null : Long.valueOf(body.get("coins").toString());
        Double rupees = body.get("rupees") == null ? null : Double.valueOf(body.get("rupees").toString());
        var tx = redeem.redeem(user, pumpToken, coins, rupees);
        return Map.of(
                "txnId", tx.getId(),
                "coins", tx.getCoins(),
                "rupees", tx.getCoins() / 100.0,
                "walletCoins", user.getWalletCoins(),
                "message", "Paid ₹" + (tx.getCoins() / 100.0) + " — show this to attendant for fuel"
        );
    }

    @GetMapping("/qr-link")
    public Map<String, String> qrLink() {
        var p = pumps.findAll().get(0);
        return Map.of("url", "/redeem?token=" + p.getRedeemToken(), "token", p.getRedeemToken());
    }

    private Map<String, Object> dto(RedeemTransaction t) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", t.getId());
        m.put("coins", t.getCoins());
        m.put("rupees", t.getCoins() / 100.0);
        m.put("businessDay", t.getBusinessDay());
        m.put("createdAt", t.getCreatedAt().toString());
        m.put("pumpName", t.getPump() == null ? "" : t.getPump().getName());
        return m;
    }
}
