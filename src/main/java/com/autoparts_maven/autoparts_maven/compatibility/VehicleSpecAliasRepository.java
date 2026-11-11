package com.autoparts_maven.autoparts_maven.compatibility;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VehicleSpecAliasRepository extends JpaRepository<VehicleSpecAlias, Long> {

    Optional<VehicleSpecAlias> findBySignature(String signature);
}
