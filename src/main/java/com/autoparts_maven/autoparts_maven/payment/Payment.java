package com.autoparts_maven.autoparts_maven.payment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/** A payment attempt for an order at the payment provider. */
@Entity
@Table(name = "payments")
@Getter
@Setter
@NoArgsConstructor
public class Payment {

    public enum Provider {
        MERCADO_PAGO
    }

    public enum Method {
        PIX,
        CREDIT_CARD
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Provider provider;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Method method;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    /** Sent as X-Idempotency-Key: retrying the same attempt never charges twice. */
    @Column(name = "idempotency_key", nullable = false, unique = true, length = 64)
    private String idempotencyKey;

    @Column(name = "provider_payment_id", length = 64)
    private String providerPaymentId;

    @Column(name = "provider_status", length = 40)
    private String providerStatus;

    @Column(name = "provider_status_detail", length = 80)
    private String providerStatusDetail;

    @Column(name = "pix_qr_code", columnDefinition = "text")
    private String pixQrCode;

    @Column(name = "pix_qr_code_base64", columnDefinition = "text")
    private String pixQrCodeBase64;

    @Column(name = "pix_ticket_url", length = 500)
    private String pixTicketUrl;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "refunded_at")
    private Instant refundedAt;

    /** Set when money and order disagree (late payment, amount mismatch, chargeback); shown to admins. */
    @Column(name = "needs_attention", nullable = false)
    private boolean needsAttention;

    @Column(name = "attention_reason", length = 200)
    private String attentionReason;

    @Version
    @Column(nullable = false)
    private Long version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    void flag(String reason) {
        needsAttention = true;
        attentionReason = reason.length() > 200 ? reason.substring(0, 200) : reason;
    }
}
