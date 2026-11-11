package com.autoparts_maven.autoparts_maven.compatibility;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/** A vehicle model as parts catalogs describe it ("ficha"). Text fields are normalized. */
@Entity
@Table(name = "vehicle_specs")
@Getter
@Setter
@NoArgsConstructor
public class VehicleSpec {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 60)
    private String brand;

    @Column(nullable = false, length = 100)
    private String model;

    @Column(length = 100)
    private String version;

    @Column(length = 60)
    private String engine;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private FuelType fuel;

    @Column(name = "year_start", nullable = false)
    private Integer yearStart;

    @Column(name = "year_end", nullable = false)
    private Integer yearEnd;

    @Column(name = "oil_capacity_liters", precision = 4, scale = 2)
    private BigDecimal oilCapacityLiters;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
