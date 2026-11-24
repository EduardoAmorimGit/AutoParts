package com.autoparts_maven.autoparts_maven.payment;

import java.math.BigDecimal;

/** A refund was confirmed by the provider; published inside the transaction that recorded it. */
public record PaymentRefundedEvent(Long orderId, Long paymentId, BigDecimal amount, String method) {
}
