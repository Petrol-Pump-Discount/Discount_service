package com.petrolpump.discount.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Simple in-memory throttle for sensitive endpoints. */
@Component
public class RateLimitService {
    private final Map<String, Instant> last = new ConcurrentHashMap<>();

    public void check(String key, int minIntervalSeconds) {
        Instant now = Instant.now();
        Instant prev = last.get(key);
        if (prev != null && prev.plusSeconds(minIntervalSeconds).isAfter(now)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Too many requests — wait " + minIntervalSeconds + "s and try again");
        }
        last.put(key, now);
    }
}
