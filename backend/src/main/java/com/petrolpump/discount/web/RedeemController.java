package com.petrolpump.discount.web;

import com.petrolpump.discount.domain.RedeemTransaction;
import com.petrolpump.discount.repo.PumpRepository;
import com.petrolpump.discount.repo.RedeemTransactionRepository;
import com.petrolpump.discount.service.AuthService;
import com.petrolpump.discount.service.RedeemService;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/redeem")
public class RedeemController {
    private final AuthService auth;
    private final RedeemService redeem;
    private final PumpRepository pumps;
    private final RedeemTransactionRepository redeems;

    public RedeemController(AuthService auth, RedeemService redeem, PumpRepository pumps,
                            RedeemTransactionRepository redeems) {
        this.auth = auth; this.redeem = redeem; this.pumps = pumps; this.redeems = redeems;
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

    @PostMapping("/pay")
    public Map<String, Object> pay(@RequestHeader("X-Session-Token") String session,
                                   @RequestBody Map<String, Object> body) {
        var user = auth.requireUser(session);
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
