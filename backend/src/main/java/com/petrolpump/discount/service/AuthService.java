package com.petrolpump.discount.service;

import com.petrolpump.discount.domain.*;
import com.petrolpump.discount.repo.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AuthService {
    private final AppUserRepository users;
    private final UserSessionRepository sessions;
    private final String devOtp;
    private final Map<String, String> pendingOtps = new ConcurrentHashMap<>();

    public AuthService(AppUserRepository users, UserSessionRepository sessions,
                       @Value("${app.otp-dev-code:123456}") String devOtp) {
        this.users = users;
        this.sessions = sessions;
        this.devOtp = devOtp;
    }

    public void requestOtp(String phone) {
        phone = normalizePhone(phone);
        pendingOtps.put(phone, devOtp);
    }

    @Transactional
    public String verifyOtp(String phone, String otp, String name, UserRole roleIfNew) {
        phone = normalizePhone(phone);
        String expected = pendingOtps.getOrDefault(phone, devOtp);
        if (!expected.equals(otp)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid OTP");
        }
        pendingOtps.remove(phone);
        final String phoneFinal = phone;
        final String nameFinal = name;
        final UserRole roleFinal = roleIfNew == null ? UserRole.DRIVER : roleIfNew;
        AppUser user = users.findByPhone(phoneFinal).orElseGet(() -> {
            AppUser u = new AppUser();
            u.setPhone(phoneFinal);
            u.setName(nameFinal);
            u.setRole(roleFinal);
            return users.save(u);
        });
        if (name != null && !name.isBlank() && (user.getName() == null || user.getName().isBlank())) {
            user.setName(name);
        }
        UserSession s = new UserSession();
        s.setToken(UUID.randomUUID().toString().replace("-", ""));
        s.setUser(user);
        s.setExpiresAt(Instant.now().plus(30, ChronoUnit.DAYS));
        sessions.save(s);
        return s.getToken();
    }

    public AppUser requireUser(String token) {
        if (token == null || token.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Login required");
        }
        UserSession s = sessions.findById(token)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid session"));
        if (s.getExpiresAt().isBefore(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Session expired");
        }
        return s.getUser();
    }

    public AppUser requireRole(String token, UserRole... roles) {
        AppUser u = requireUser(token);
        for (UserRole r : roles) if (u.getRole() == r) return u;
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not allowed");
    }

    public static String normalizePhone(String phone) {
        String digits = phone == null ? "" : phone.replaceAll("\\D", "");
        if (digits.length() > 10) digits = digits.substring(digits.length() - 10);
        if (digits.length() != 10) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid phone");
        }
        return digits;
    }
}
