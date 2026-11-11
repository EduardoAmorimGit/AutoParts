package com.autoparts_maven.autoparts_maven.compatibility;

import com.autoparts_maven.autoparts_maven.vehicle.Vehicle;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface VehicleSpecAssignmentRepository extends JpaRepository<VehicleSpecAssignment, Long> {

    /** Vehicles that were looked up but are not resolved to any spec yet (the admin queue). */
    @Query(value = """
            select v from Vehicle v
            where not exists (select a from VehicleSpecAssignment a where a.vehicleId = v.id)
            """, countQuery = """
            select count(v) from Vehicle v
            where not exists (select a from VehicleSpecAssignment a where a.vehicleId = v.id)
            """)
    Page<Vehicle> findUnassignedVehicles(Pageable pageable);

    @Query("""
            select v from Vehicle v
            where not exists (select a from VehicleSpecAssignment a where a.vehicleId = v.id)
            """)
    List<Vehicle> findAllUnassignedVehicles();

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from VehicleSpecAssignment a where a.matchType = :matchType")
    int deleteByMatchType(VehicleSpecAssignment.MatchType matchType);
}
