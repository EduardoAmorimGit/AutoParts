package com.autoparts_maven.autoparts_maven.compatibility;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Which spec a looked-up vehicle resolved to. Kept in the compatibility module (keyed by the
 * vehicle id) so the vehicle module does not depend on specs.
 */
@Entity
@Table(name = "vehicle_spec_assignments")
@Getter
@Setter
@NoArgsConstructor
public class VehicleSpecAssignment {

    public enum MatchType {
        AUTO,
        MANUAL
    }

    @Id
    @Column(name = "vehicle_id")
    private Long vehicleId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "spec_id", nullable = false)
    private VehicleSpec spec;

    @Enumerated(EnumType.STRING)
    @Column(name = "match_type", nullable = false, length = 10)
    private MatchType matchType;

    @Column(name = "assigned_at", nullable = false)
    private Instant assignedAt;

    VehicleSpecAssignment(Long vehicleId, VehicleSpec spec, MatchType matchType, Instant assignedAt) {
        this.vehicleId = vehicleId;
        this.spec = spec;
        this.matchType = matchType;
        this.assignedAt = assignedAt;
    }
}
