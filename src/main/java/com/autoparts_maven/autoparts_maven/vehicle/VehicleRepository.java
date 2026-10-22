package com.autoparts_maven.autoparts_maven.vehicle;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VehicleRepository extends JpaRepository<Vehicle, Long> {

    Optional<Vehicle> findByPlate(String plate);

    boolean existsByPlate(String plate);
}
