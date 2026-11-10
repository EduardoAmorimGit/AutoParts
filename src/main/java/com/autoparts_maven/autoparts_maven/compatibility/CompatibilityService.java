package com.autoparts_maven.autoparts_maven.compatibility;

import com.autoparts_maven.autoparts_maven.common.PageResponse;
import com.autoparts_maven.autoparts_maven.product.ProductNotFoundException;
import com.autoparts_maven.autoparts_maven.product.ProductRepository;
import com.autoparts_maven.autoparts_maven.product.dto.ProductResponse;
import com.autoparts_maven.autoparts_maven.vehicle.VehicleNotFoundException;
import com.autoparts_maven.autoparts_maven.vehicle.VehicleRepository;
import com.autoparts_maven.autoparts_maven.vehicle.dto.VehicleResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Explicit product/vehicle compatibility. A product is compatible with a vehicle only when an
 * administrator registered the link; nothing is inferred from brand, model or similarity.
 */
@Service
@RequiredArgsConstructor
public class CompatibilityService {

    private final ProductRepository productRepository;
    private final VehicleRepository vehicleRepository;
    private final ProductVehicleRepository productVehicleRepository;

    /** Idempotent: linking an already linked pair is a no-op. */
    @Transactional
    public void link(Long productId, Long vehicleId) {
        ensureProductExists(productId);
        ensureVehicleExists(vehicleId);
        productVehicleRepository.insertIfAbsent(productId, vehicleId);
    }

    /** Idempotent: unlinking a pair that is not linked is a no-op. */
    @Transactional
    public void unlink(Long productId, Long vehicleId) {
        ensureProductExists(productId);
        ensureVehicleExists(vehicleId);
        productVehicleRepository.deleteLink(productId, vehicleId);
    }

    /** Administrative view: every vehicle linked to the product. */
    @Transactional(readOnly = true)
    public PageResponse<VehicleResponse> findVehiclesForProduct(Long productId, Pageable pageable) {
        ensureProductExists(productId);
        return PageResponse.from(vehicleRepository
                .findAll(CompatibilitySpecifications.vehiclesLinkedTo(productId), pageable)
                .map(VehicleResponse::from));
    }

    /** Customer view: active products with a registered link to the vehicle. */
    @Transactional(readOnly = true)
    public PageResponse<ProductResponse> findProductsForVehicle(Long vehicleId, Pageable pageable) {
        ensureVehicleExists(vehicleId);
        return PageResponse.from(productRepository
                .findAll(CompatibilitySpecifications.activeProductsLinkedTo(vehicleId), pageable)
                .map(ProductResponse::from));
    }

    private void ensureProductExists(Long productId) {
        if (!productRepository.existsById(productId)) {
            throw new ProductNotFoundException(productId);
        }
    }

    private void ensureVehicleExists(Long vehicleId) {
        if (!vehicleRepository.existsById(vehicleId)) {
            throw new VehicleNotFoundException(vehicleId);
        }
    }
}
