package com.autoparts_maven.autoparts_maven.order;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

public enum OrderStatus {
    /** Created at checkout; stock reserved until payment or expiry. */
    AWAITING_PAYMENT,
    PAID,
    PREPARING,
    SHIPPED,
    DELIVERED,
    /** Cancelled by the customer, by an admin, or expired without payment; stock returned. */
    CANCELLED;

    private static final Map<OrderStatus, Set<OrderStatus>> NEXT = Map.of(
            AWAITING_PAYMENT, EnumSet.of(PAID, CANCELLED),
            PAID, EnumSet.of(PREPARING, CANCELLED),
            PREPARING, EnumSet.of(SHIPPED, CANCELLED),
            SHIPPED, EnumSet.of(DELIVERED),
            DELIVERED, EnumSet.noneOf(OrderStatus.class),
            CANCELLED, EnumSet.noneOf(OrderStatus.class));

    public boolean canMoveTo(OrderStatus next) {
        return NEXT.get(this).contains(next);
    }
}
