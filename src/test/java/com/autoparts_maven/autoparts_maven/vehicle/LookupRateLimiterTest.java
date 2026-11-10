package com.autoparts_maven.autoparts_maven.vehicle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class LookupRateLimiterTest {

    private final MutableClock clock = new MutableClock();
    private final LookupRateLimiter limiter = new LookupRateLimiter(2, Duration.ofMinutes(1), clock);

    @Test
    void allowsUpToTheLimitWithinAWindow() {
        assertThatCode(() -> {
            limiter.acquire("10.0.0.1");
            limiter.acquire("10.0.0.1");
        }).doesNotThrowAnyException();
    }

    @Test
    void rejectsRequestsAboveTheLimitWithRetryAfter() {
        limiter.acquire("10.0.0.1");
        limiter.acquire("10.0.0.1");

        assertThatThrownBy(() -> limiter.acquire("10.0.0.1"))
                .isInstanceOf(LookupRateLimitExceededException.class)
                .satisfies(e -> assertThat(((LookupRateLimitExceededException) e).getRetryAfterSeconds())
                        .isBetween(1L, 60L));
    }

    @Test
    void countsEachClientSeparately() {
        limiter.acquire("10.0.0.1");
        limiter.acquire("10.0.0.1");

        assertThatCode(() -> limiter.acquire("10.0.0.2")).doesNotThrowAnyException();
    }

    @Test
    void resetsAfterTheWindow() {
        limiter.acquire("10.0.0.1");
        limiter.acquire("10.0.0.1");

        clock.advance(Duration.ofMinutes(1));

        assertThatCode(() -> limiter.acquire("10.0.0.1")).doesNotThrowAnyException();
    }

    @Test
    void rejectsInvalidConfiguration() {
        assertThatThrownBy(() -> new LookupRateLimiter(0, Duration.ofMinutes(1), clock))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LookupRateLimiter(1, Duration.ZERO, clock))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static final class MutableClock extends Clock {

        private Instant now = Instant.parse("2026-01-01T00:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
