package com.autoparts_maven.autoparts_maven.returns;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

public enum ReturnStatus {
    /** Waiting for an admin answer (the e-mail confirming receipt is sent at once). */
    REQUESTED,
    /** Accepted; the store collects the items at its own cost. */
    APPROVED,
    /** Items are back at the store. */
    RECEIVED,
    REFUNDED,
    /** Defect solved with a new unit instead of money. */
    REPLACED,
    REJECTED;

    private static final Map<ReturnStatus, Set<ReturnStatus>> NEXT = Map.of(
            REQUESTED, EnumSet.of(APPROVED, REJECTED),
            // Refunding before the items arrive is allowed: it favours the customer.
            APPROVED, EnumSet.of(RECEIVED, REFUNDED, REPLACED, REJECTED),
            RECEIVED, EnumSet.of(REFUNDED, REPLACED, REJECTED),
            REFUNDED, EnumSet.noneOf(ReturnStatus.class),
            REPLACED, EnumSet.noneOf(ReturnStatus.class),
            REJECTED, EnumSet.noneOf(ReturnStatus.class));

    public boolean canMoveTo(ReturnStatus next) {
        return NEXT.get(this).contains(next);
    }

    public boolean isOpen() {
        return this == REQUESTED || this == APPROVED || this == RECEIVED;
    }
}
