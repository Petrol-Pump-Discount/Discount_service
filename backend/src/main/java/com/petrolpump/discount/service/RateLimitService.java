package com.petrolpump.discount.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory throttle for sensitive endpoints. */
@Component
public class RateLimitService {
    private final Map<String, Instant> last = new ConcurrentHashMap<>();
    private final Map<String, Deque<Long>> windows = new ConcurrentHashMap<>();

    /** Minimum gap between calls (redeem OTP, etc.). */
    public void check(String key, int minIntervalSeconds) {
        Instant now = Instant.now();
        Instant prev = last.get(key);
        if (prev != null && prev.plusSeconds(minIntervalSeconds).isAfter(now)) {
            long wait = Math.max(1, minIntervalSeconds - (now.getEpochSecond() - prev.getEpochSecond()));
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Too many requests — wait " + wait + "s and try again");
        }
        last.put(key, now);
    }

    /**
     * Allow up to {@code max} calls inside {@code windowSeconds} (for uploads / ~3–5 QPS).
     * Does not block a single user on every retry the way a hard cooldown does.
     */
    public void checkWindow(String key, int max, int windowSeconds) {
        long now = System.currentTimeMillis();
        long cutoff = now - windowSeconds * 1000L;
        Deque<Long> q = windows.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (q) {
            while (!q.isEmpty() && q.peekFirst() < cutoff) {
                q.pollFirst();
            }
            if (q.size() >= max) {
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                        "Too many uploads in a short time. Wait a few seconds and try again.");
            }
            q.addLast(now);
        }
    }
}
