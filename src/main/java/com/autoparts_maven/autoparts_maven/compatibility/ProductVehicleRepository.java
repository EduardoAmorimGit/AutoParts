package com.autoparts_maven.autoparts_maven.compatibility;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface ProductVehicleRepository extends JpaRepository<ProductVehicle, Long> {

    /**
     * Idempotent, race-free link: concurrent requests for the same pair cannot violate
     * {@code uq_product_vehicle}. Returns 1 when a link was created, 0 when it already existed.
     */
    @Modifying
    @Query(value = """
            INSERT INTO product_vehicle (product_id, vehicle_id, created_at)
            VALUES (:productId, :vehicleId, now())
            ON CONFLICT (product_id, vehicle_id) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(Long productId, Long vehicleId);

    @Modifying
    @Query("delete from ProductVehicle pv where pv.product.id = :productId and pv.vehicle.id = :vehicleId")
    int deleteLink(Long productId, Long vehicleId);
}
