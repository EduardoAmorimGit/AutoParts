package com.autoparts_maven.autoparts_maven.vehicle;

public class LookupRateLimitExceededException extends RuntimeException {

    private final long retryAfterSeconds;

    public LookupRateLimitExceededException(long retryAfterSeconds) {
        super("Too many plate lookups; try again later");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
