package com.autoparts_maven.autoparts_maven.config;

import com.autoparts_maven.autoparts_maven.common.ratelimit.FixedWindowRateLimiter;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RateLimitConfig {

    /** Paid plate lookups per client IP. Stored vehicles do not count. */
    @Bean
    FixedWindowRateLimiter lookupRateLimiter(
            @Value("${vehicle-lookup.rate-limit.max-requests:10}") int maxRequests,
            @Value("${vehicle-lookup.rate-limit.window:1m}") Duration window) {
        return new FixedWindowRateLimiter(maxRequests, window);
    }

    /** Login and registration attempts per client IP (brute force and account spam). */
    @Bean
    FixedWindowRateLimiter authRateLimiter(
            @Value("${auth.rate-limit.max-requests:10}") int maxRequests,
            @Value("${auth.rate-limit.window:1m}") Duration window) {
        return new FixedWindowRateLimiter(maxRequests, window);
    }
}
