package com.autoparts_maven.autoparts_maven.compatibility;

import com.autoparts_maven.autoparts_maven.product.Product;
import com.autoparts_maven.autoparts_maven.vehicle.Vehicle;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductVehicleRepository extends JpaRepository<ProductVehicle, Long> {

    Optional<ProductVehicle> findByProductAndVehicle(Product product, Vehicle vehicle);

    Page<ProductVehicle> findByProduct(Product product, Pageable pageable);

    Page<ProductVehicle> findByVehicle(Vehicle vehicle, Pageable pageable);

    void deleteByProductAndVehicle(Product product, Vehicle vehicle);
}
