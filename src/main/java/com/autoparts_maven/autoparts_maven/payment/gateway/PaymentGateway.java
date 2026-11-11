package com.autoparts_maven.autoparts_maven.payment.gateway;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Payment provider port. Only the implementation knows provider URLs, credentials and JSON; the
 * domain works with these records. Every creating call carries an idempotency key so a retry of
 * the same attempt never produces a second charge.
 */
public interface PaymentGateway {

    record Payer(String email, String identificationType, String identificationNumber) {
    }

    record PixCharge(String idempotencyKey, String externalReference, BigDecimal amount, String description,
            Payer payer, Instant expiresAt) {
    }

    record CardCharge(String idempotencyKey, String externalReference, BigDecimal amount, String description,
            Payer payer, String token, String paymentMethodId, String issuerId, int installments) {
    }

    /** 3-D Secure challenge the frontend must show before the card payment can complete. */
    record Challenge(String url, String creq) {
    }

    record GatewayPayment(String id, String status, String statusDetail, BigDecimal amount,
            String externalReference, String pixQrCode, String pixQrCodeBase64, String pixTicketUrl,
            Instant expiresAt, Challenge challenge) {
    }

    GatewayPayment createPix(PixCharge charge);

    GatewayPayment createCard(CardCharge charge);

    GatewayPayment fetch(String paymentId);

    GatewayPayment refund(String paymentId, String idempotencyKey);

    /** Public key the frontend needs for card tokenization (never the access token). */
    String publicKey();
}
