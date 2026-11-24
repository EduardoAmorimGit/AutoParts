package com.autoparts_maven.autoparts_maven.returns;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/** A customer's request to return delivered items, with the money due computed when it is opened. */
@Entity
@Table(name = "return_requests")
@Getter
@Setter
@NoArgsConstructor
public class ReturnRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReturnType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReturnStatus status;

    @Column(name = "customer_reason", length = 1000)
    private String customerReason;

    @Column(name = "items_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal itemsAmount;

    @Column(name = "delivery_fee_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal deliveryFeeAmount = BigDecimal.ZERO;

    @Column(name = "refund_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal refundAmount;

    @Column(name = "admin_note", length = 1000)
    private String adminNote;

    @Column(name = "rejection_reason", length = 1000)
    private String rejectionReason;

    @Column(nullable = false)
    private boolean restocked;

    @Column(name = "decided_by")
    private Long decidedBy;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "received_at")
    private Instant receivedAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Version
    @Column(nullable = false)
    private Long version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "returnRequest", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<ReturnRequestItem> items = new ArrayList<>();

    void addItem(Long orderItemId, int quantity) {
        ReturnRequestItem item = new ReturnRequestItem();
        item.setReturnRequest(this);
        item.setOrderItemId(orderItemId);
        item.setQuantity(quantity);
        items.add(item);
    }

    void moveTo(ReturnStatus next) {
        if (!status.canMoveTo(next)) {
            throw new InvalidReturnTransitionException(id, status, next);
        }
        status = next;
    }

    @Entity
    @Table(name = "return_request_items")
    @Getter
    @Setter
    @NoArgsConstructor
    public static class ReturnRequestItem {

        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        @ManyToOne(fetch = FetchType.LAZY, optional = false)
        @JoinColumn(name = "return_request_id", nullable = false)
        private ReturnRequest returnRequest;

        @Column(name = "order_item_id", nullable = false)
        private Long orderItemId;

        @Column(nullable = false)
        private int quantity;
    }
}
