package com.autoparts_maven.autoparts_maven.notification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

/** An e-mail to a customer, stored before it is sent (outbox) and kept as proof afterwards. */
@Entity
@Table(name = "email_outbox")
@Getter
@Setter
@NoArgsConstructor
public class EmailOutbox {

    public enum Status {
        PENDING,
        SENT,
        /** Gave up after the maximum number of attempts; shown to the admins. */
        FAILED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "order_id")
    private Long orderId;

    @Column(nullable = false, length = 40)
    private String kind;

    @Column(name = "to_address", nullable = false, length = 255)
    private String toAddress;

    @Column(nullable = false, length = 200)
    private String subject;

    @Column(nullable = false, columnDefinition = "text")
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.PENDING;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "last_error", length = 500)
    private String lastError;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt = Instant.now();

    /** Body carries a secret (e.g. a reset link): redacted once sent or given up, never shown to admins. */
    @Column(nullable = false)
    private boolean sensitive;

    @Column(name = "sent_at")
    private Instant sentAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
