package com.autoparts_maven.autoparts_maven.common.ratelimit;

import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fixed-window request limit per key (usually the client IP address).
 *
 * <p>State is in memory, so the limit applies per application instance. Behind a reverse proxy,
 * configure {@code server.forward-headers-strategy} so the client address is the real caller,
 * not the proxy. A shared limiter (gateway or Redis) replaces this when running several instances.
 */
public class FixedWindowRateLimiter {

    private static final int CLEANUP_THRESHOLD = 10_000;

    private final int maxRequests;
    private final long windowMillis;
    private final Clock clock;
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

    public FixedWindowRateLimiter(int maxRequests, Duration window) {
        this(maxRequests, window, Clock.systemUTC());
    }

    public FixedWindowRateLimiter(int maxRequests, Duration window, Clock clock) {
        if (maxRequests < 1 || window.isZero() || window.isNegative()) {
            throw new IllegalArgumentException("Rate limit must allow at least one request per positive window");
        }
        this.maxRequests = maxRequests;
        this.windowMillis = window.toMillis();
        this.clock = clock;
    }

    /** Counts one request for {@code key}; throws {@link RateLimitExceededException} above the limit. */
    public void acquire(String key) {
        long now = clock.millis();
        Window window = windows.compute(key == null ? "unknown" : key,
                (k, current) -> current == null || current.expiredAt(now)
                        ? new Window(now + windowMillis, 1)
                        : new Window(current.resetAt(), current.count() + 1));

        if (windows.size() > CLEANUP_THRESHOLD) {
            windows.values().removeIf(entry -> entry.expiredAt(now));
        }

        if (window.count() > maxRequests) {
            long retryAfterSeconds = Math.max(1, (window.resetAt() - now + 999) / 1000);
            throw new RateLimitExceededException(retryAfterSeconds);
        }
    }

    private record Window(long resetAt, int count) {

        boolean expiredAt(long now) {
            return now >= resetAt;
        }
    }
}
