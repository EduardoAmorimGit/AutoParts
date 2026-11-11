package com.autoparts_maven.autoparts_maven.compatibility;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface VehicleSpecRepository extends JpaRepository<VehicleSpec, Long> {

    @Query("select s from VehicleSpec s where s.brand = :brand and :year between s.yearStart and s.yearEnd")
    List<VehicleSpec> findCandidates(String brand, int year);

    /** Null-safe identity lookup mirroring the unique index {@code uq_vehicle_specs_identity}. */
    @Query("""
            select s from VehicleSpec s
            where s.brand = :brand and s.model = :model
              and coalesce(s.version, '') = coalesce(:version, '')
              and coalesce(s.engine, '') = coalesce(:engine, '')
              and coalesce(cast(s.fuel as string), '') = coalesce(:fuel, '')
              and s.yearStart = :yearStart and s.yearEnd = :yearEnd
            """)
    Optional<VehicleSpec> findIdentical(String brand, String model, String version, String engine, String fuel,
            int yearStart, int yearEnd);

    @Query("""
            select s from VehicleSpec s
            where (:brand is null or s.brand = :brand)
              and (:model is null or s.model like concat(:model, '%'))
            """)
    Page<VehicleSpec> search(String brand, String model, Pageable pageable);
}
