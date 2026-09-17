package com.petrolpump.discount.web;

import com.petrolpump.discount.domain.UserRole;
import com.petrolpump.discount.service.AuthService;
import com.petrolpump.discount.service.RateLimitService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService auth;
    private final RateLimitService rateLimit;
    private final String provider;

    public AuthController(AuthService auth, RateLimitService rateLimit,
                          @Value("${app.otp.provider:console}") String provider) {
        this.auth = auth;
        this.rateLimit = rateLimit;
        this.provider = provider;
    }

    @PostMapping("/otp/request")
    public Map<String, String> request(@RequestBody Map<String, String> body, HttpServletRequest req) {
        String ip = ClientIp.of(req);
        rateLimit.checkWindow("otp-ip:" + ip, 8, 3600);
        rateLimit.check("otp-ip-burst:" + ip, 5);
        auth.requestOtp(body.get("phone"));
        return Map.of(
                "status", "ok",
                "message", "OTP sent to your mobile",
                "provider", provider
        );
    }

    @PostMapping("/otp/verify")
    public Map<String, Object> verify(@RequestBody Map<String, Object> body, HttpServletRequest req) {
        Object accepted = body.get("acceptedTerms");
        if (!(accepted instanceof Boolean) || !((Boolean) accepted)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Accept Terms & Privacy to continue");
        }
        String ip = ClientIp.of(req);
        rateLimit.checkWindow("otp-verify-ip:" + ip, 20, 3600);

        String phone = body.get("phone") == null ? null : body.get("phone").toString();
        String otp = body.get("otp") == null ? null : body.get("otp").toString();
        String name = body.get("name") == null ? null : body.get("name").toString();
        String token = auth.verifyOtp(phone, otp, name, UserRole.DRIVER);
        var user = auth.requireUser(token);
        return Map.of("token", token, "phone", user.getPhone(), "role", user.getRole().name(),
                "walletCoins", user.getWalletCoins(), "name", user.getName() == null ? "" : user.getName());
    }

    @PostMapping("/logout")
    public Map<String, String> logout(@RequestHeader(value = "X-Session-Token", required = false) String token) {
        auth.logout(token);
        return Map.of("status", "ok");
    }

    @GetMapping("/me")
    public Map<String, Object> me(@RequestHeader(value = "X-Session-Token", required = false) String token) {
        var user = auth.requireUser(token);
        return Map.of("phone", user.getPhone(), "role", user.getRole().name(),
                "walletCoins", user.getWalletCoins(), "name", user.getName() == null ? "" : user.getName());
    }
}
