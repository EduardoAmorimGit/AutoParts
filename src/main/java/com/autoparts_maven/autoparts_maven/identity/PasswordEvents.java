package com.autoparts_maven.autoparts_maven.identity;

import java.time.Instant;

/**
 * Account security events, published inside the transaction that caused them so the e-mail outbox
 * commits or rolls back together with the change.
 */
public final class PasswordEvents {

    private PasswordEvents() {
    }

    /** The raw token lives only in memory and in the e-mail; the database keeps its hash. */
    public record PasswordResetRequested(Long userId, String token, Instant expiresAt) {

        @Override
        public String toString() {
            return "PasswordResetRequested[userId=" + userId + ", expiresAt=" + expiresAt + "]";
        }
    }

    public record PasswordChanged(Long userId, Instant changedAt) {
    }
}
