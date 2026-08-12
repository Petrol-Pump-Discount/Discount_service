package com.petrolpump.discount.service;

import com.petrolpump.discount.domain.*;
import com.petrolpump.discount.repo.*;
import com.petrolpump.discount.service.otp.OtpSender;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AuthService {
    private final AppUserRepository users;
    private final UserSessionRepository sessions;
    private final OtpSender otpSender;
    private final int otpTtlSeconds;
    private final int otpMaxAttempts;
    private final SecureRandom random = new SecureRandom();
    private final Map<String, PendingOtp> pending = new ConcurrentHashMap<>();
    private final Map<String, Instant> lastRequest = new ConcurrentHashMap<>();

    public AuthService(AppUserRepository users, UserSessionRepository sessions, OtpSender otpSender,
                       @Value("${app.otp.ttl-seconds:300}") int otpTtlSeconds,
                       @Value("${app.otp.max-attempts:5}") int otpMaxAttempts) {
        this.users = users;
        this.sessions = sessions;
        this.otpSender = otpSender;
        this.otpTtlSeconds = otpTtlSeconds;
        this.otpMaxAttempts = otpMaxAttempts;
    }

    public void requestOtp(String phone) {
        phone = normalizePhone(phone);
        Instant now = Instant.now();
        Instant prev = lastRequest.get(phone);
        if (prev != null && prev.plusSeconds(30).isAfter(now)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Wait 30 seconds before requesting another OTP");
        }
        lastRequest.put(phone, now);

        String code = String.format("%06d", random.nextInt(1_000_000));
        if (!otpSender.providerVerifies()) {
            pending.put(phone, new PendingOtp(code, now.plusSeconds(otpTtlSeconds), 0));
        } else {
            pending.remove(phone);
        }
        try {
            otpSender.send(phone, code);
        } catch (Exception ex) {
            pending.remove(phone);
            org.slf4j.LoggerFactory.getLogger(AuthService.class)
                    .error("OTP send failed for {}: {}", phone, ex.toString());
            String reason = ex.getMessage() == null ? "Failed to send OTP" : ex.getMessage();
            if (reason.length() > 180) reason = reason.substring(0, 180);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, reason);
        }
    }

    @Transactional
    public String verifyOtp(String phone, String otp, String name, UserRole roleIfNew) {
        phone = normalizePhone(phone);
        if (otp == null || !otp.matches("\\d{6}")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid OTP");
        }

        boolean ok;
        if (otpSender.providerVerifies()) {
            try {
                ok = otpSender.verify(phone, otp);
            } catch (Exception ex) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid OTP");
            }
        } else {
            PendingOtp p = pending.get(phone);
            if (p == null || p.expiresAt.isBefore(Instant.now())) {
                pending.remove(phone);
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "OTP expired — request a new one");
            }
            if (p.attempts >= otpMaxAttempts) {
                pending.remove(phone);
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Too many attempts — request a new OTP");
            }
            p.attempts++;
            ok = p.code.equals(otp);
            if (ok) pending.remove(phone);
        }
        if (!ok) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid OTP");
        }

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
        if (digits.length() != 10 || digits.charAt(0) < '6') {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid phone");
        }
        return digits;
    }

    private static final class PendingOtp {
        final String code;
        final Instant expiresAt;
        int attempts;

        PendingOtp(String code, Instant expiresAt, int attempts) {
            this.code = code;
            this.expiresAt = expiresAt;
            this.attempts = attempts;
        }
    }
}
