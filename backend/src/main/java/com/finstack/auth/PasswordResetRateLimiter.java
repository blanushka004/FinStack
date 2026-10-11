package com.finstack.auth;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/** Local, bounded best-effort throttle. Deployments with multiple nodes need a shared gateway limit. */
@Component
public class PasswordResetRateLimiter {
    private static final Duration WINDOW = Duration.ofMinutes(15);
    private final ConcurrentHashMap<String, Attempt> attempts = new ConcurrentHashMap<>();

    public boolean allow(String email, String remoteAddress) {
        Instant now = Instant.now();
        if (attempts.size() > 20_000) attempts.entrySet().removeIf(entry -> entry.getValue().started().plus(WINDOW).isBefore(now));
        if (attempts.size() >= 20_000 && (!attempts.containsKey("email:" + email) || !attempts.containsKey("ip:" + remoteAddress))) return false;
        return take("email:" + email, 5, now) && take("ip:" + remoteAddress, 30, now);
    }

    private boolean take(String key, int limit, Instant now) {
        Attempt result = attempts.compute(key, (ignored, previous) -> {
            if (previous == null || !previous.started().plus(WINDOW).isAfter(now)) return new Attempt(now, 1);
            return new Attempt(previous.started(), Math.min(previous.count() + 1, limit + 1));
        });
        return result.count() <= limit;
    }

    private record Attempt(Instant started, int count) { }
}
