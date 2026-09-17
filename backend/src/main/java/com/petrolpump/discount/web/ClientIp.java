package com.petrolpump.discount.web;

import jakarta.servlet.http.HttpServletRequest;

/** Best-effort client IP behind Cloudflare / nginx. */
public final class ClientIp {
    private ClientIp() {}

    public static String of(HttpServletRequest req) {
        if (req == null) return "unknown";
        String xff = req.getHeader("CF-Connecting-IP");
        if (xff != null && !xff.isBlank()) return xff.trim();
        xff = req.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        String remote = req.getRemoteAddr();
        return remote == null || remote.isBlank() ? "unknown" : remote;
    }
}
