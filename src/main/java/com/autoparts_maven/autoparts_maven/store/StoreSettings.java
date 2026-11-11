package com.autoparts_maven.autoparts_maven.store;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UpdateTimestamp;

/** The single row (id = 1) of store-wide settings, created by migration V10. */
@Entity
@Table(name = "store_settings")
@Getter
@Setter
@NoArgsConstructor
public class StoreSettings {

    static final short SINGLETON_ID = 1;

    @Id
    private Short id;

    /** Fixed delivery fee; null until the owners configure it (checkout is blocked meanwhile). */
    @Column(name = "delivery_fee", precision = 10, scale = 2)
    private BigDecimal deliveryFee;

    @Column(name = "updated_by")
    private Long updatedBy;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
