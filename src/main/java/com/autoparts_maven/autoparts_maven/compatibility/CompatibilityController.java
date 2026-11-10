package com.autoparts_maven.autoparts_maven.compatibility;

import com.autoparts_maven.autoparts_maven.common.PageResponse;
import com.autoparts_maven.autoparts_maven.product.dto.ProductResponse;
import com.autoparts_maven.autoparts_maven.vehicle.dto.VehicleResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Compatibility endpoints. Access rules live in {@code SecurityConfig}: managing links and listing
 * the vehicles (plates) of a product require ADMIN; listing the products of a vehicle is public.
 */
@RestController
@RequiredArgsConstructor
public class CompatibilityController {

    private final CompatibilityService compatibilityService;

    @PutMapping("/api/products/{productId}/vehicles/{vehicleId}")
    public ResponseEntity<Void> link(@PathVariable Long productId, @PathVariable Long vehicleId) {
        compatibilityService.link(productId, vehicleId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/api/products/{productId}/vehicles/{vehicleId}")
    public ResponseEntity<Void> unlink(@PathVariable Long productId, @PathVariable Long vehicleId) {
        compatibilityService.unlink(productId, vehicleId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/products/{productId}/vehicles")
    public PageResponse<VehicleResponse> findVehiclesForProduct(
            @PathVariable Long productId,
            @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.ASC) Pageable pageable) {
        return compatibilityService.findVehiclesForProduct(productId, pageable);
    }

    @GetMapping("/api/vehicles/{vehicleId}/products")
    public PageResponse<ProductResponse> findProductsForVehicle(
            @PathVariable Long vehicleId,
            @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.ASC) Pageable pageable) {
        return compatibilityService.findProductsForVehicle(vehicleId, pageable);
    }
}
