package com.autoparts_maven.autoparts_maven.order;

/**
 * Published inside the transaction that changed the order, so listeners that write (e.g. the
 * e-mail outbox) commit or roll back together with the change.
 */
public record OrderEvent(Type type, Long orderId) {

    public enum Type {
        PLACED,
        PAID,
        SHIPPED,
        DELIVERED,
        CANCELLED
    }
}
