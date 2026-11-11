package com.autoparts_maven.autoparts_maven.payment;

/** Our view of a provider payment status. */
public enum PaymentStatus {
    PENDING,
    APPROVED,
    REJECTED,
    CANCELLED,
    REFUNDED,
    CHARGED_BACK;

    /** Mercado Pago "status" values (pending, in_process, in_mediation, approved, ...). */
    public static PaymentStatus fromMercadoPago(String status) {
        if (status == null) {
            return PENDING;
        }
        return switch (status) {
            case "approved", "authorized" -> APPROVED;
            case "rejected" -> REJECTED;
            case "cancelled" -> CANCELLED;
            case "refunded" -> REFUNDED;
            case "charged_back" -> CHARGED_BACK;
            default -> PENDING; // pending, in_process, in_mediation
        };
    }

    public boolean isFinal() {
        return this != PENDING;
    }
}
