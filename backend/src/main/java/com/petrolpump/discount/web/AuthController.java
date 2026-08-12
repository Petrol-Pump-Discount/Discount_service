package com.petrolpump.discount.web;

import com.petrolpump.discount.domain.UserRole;
import com.petrolpump.discount.service.AuthService;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService auth;
    public AuthController(AuthService auth) { this.auth = auth; }

    @PostMapping("/otp/request")
    public Map<String, String> request(@RequestBody Map<String, String> body) {
        auth.requestOtp(body.get("phone"));
        return Map.of("status", "ok", "devOtp", "123456");
    }

    @PostMapping("/otp/verify")
    public Map<String, Object> verify(@RequestBody Map<String, String> body) {
        String token = auth.verifyOtp(body.get("phone"), body.get("otp"), body.get("name"), UserRole.DRIVER);
        var user = auth.requireUser(token);
        return Map.of("token", token, "phone", user.getPhone(), "role", user.getRole().name(),
                "walletCoins", user.getWalletCoins(), "name", user.getName() == null ? "" : user.getName());
    }

    @GetMapping("/me")
    public Map<String, Object> me(@RequestHeader(value = "X-Session-Token", required = false) String token) {
        var user = auth.requireUser(token);
        return Map.of("phone", user.getPhone(), "role", user.getRole().name(),
                "walletCoins", user.getWalletCoins(), "name", user.getName() == null ? "" : user.getName());
    }
}
