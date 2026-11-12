package com.autoparts_maven.autoparts_maven.delivery;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
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

/** A group of districts (bairros) sharing one delivery fee, edited from the admin panel. */
@Entity
@Table(name = "delivery_regions")
@Getter
@Setter
@NoArgsConstructor
public class DeliveryRegion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 80)
    private String name;

    /** Null until the admins set it; a region without a fee serves nobody. */
    @Column(precision = 10, scale = 2)
    private BigDecimal fee;

    /** An inactive region keeps its districts but serves none of them (checkout is refused). */
    @Column(nullable = false)
    private boolean active = true;

    @ElementCollection
    @CollectionTable(name = "delivery_region_districts", joinColumns = @JoinColumn(name = "region_id"))
    @OrderBy("name")
    private List<RegionDistrict> districts = new ArrayList<>();

    @Column(name = "updated_by")
    private Long updatedBy;

    @Version
    @Column(nullable = false)
    private Long version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
