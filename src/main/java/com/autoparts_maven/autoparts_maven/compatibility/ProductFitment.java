package com.autoparts_maven.autoparts_maven.compatibility;

import com.autoparts_maven.autoparts_maven.product.Product;
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
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/** "Product X fits spec Y", with where that information came from and its review status. */
@Entity
@Table(name = "product_fitments")
@Getter
@Setter
@NoArgsConstructor
public class ProductFitment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "spec_id", nullable = false)
    private VehicleSpec spec;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FitmentStatus status;

    @Column(nullable = false, length = 300)
    private String source;

    @Column(length = 500)
    private String notes;

    /** Id of the administrator who validated or rejected it (users.id). */
    @Column(name = "reviewed_by")
    private Long reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    void review(FitmentStatus decision, Long reviewerId, Instant when) {
        this.status = decision;
        this.reviewedBy = reviewerId;
        this.reviewedAt = when;
    }
}
