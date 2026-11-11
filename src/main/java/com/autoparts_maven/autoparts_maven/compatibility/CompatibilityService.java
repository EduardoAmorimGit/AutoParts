package com.autoparts_maven.autoparts_maven.compatibility;

import com.autoparts_maven.autoparts_maven.common.PageResponse;
import com.autoparts_maven.autoparts_maven.compatibility.dto.CompatibilityDtos.CompatibleProductsResponse;
import com.autoparts_maven.autoparts_maven.compatibility.dto.CompatibilityDtos.VehicleSpecResponse;
import com.autoparts_maven.autoparts_maven.product.Product;
import com.autoparts_maven.autoparts_maven.product.ProductRepository;
import com.autoparts_maven.autoparts_maven.product.dto.ProductResponse;
import com.autoparts_maven.autoparts_maven.vehicle.Vehicle;
import com.autoparts_maven.autoparts_maven.vehicle.VehicleNotFoundException;
import com.autoparts_maven.autoparts_maven.vehicle.VehicleRepository;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Customer view of compatibility: plate -> vehicle -> spec -> validated fitments -> active products.
 * Nothing is inferred: a product appears only through a VALIDATED fitment of the vehicle's spec.
 */
@Service
@RequiredArgsConstructor
public class CompatibilityService {

    public static final String RESOLVED = "RESOLVED";
    public static final String PENDING = "PENDING";

    private final VehicleRepository vehicleRepository;
    private final ProductRepository productRepository;
    private final SpecResolutionService specResolutionService;

    @Transactional
    public CompatibleProductsResponse findProductsForVehicle(Long vehicleId, Pageable pageable) {
        Vehicle vehicle = vehicleRepository.findById(vehicleId)
                .orElseThrow(() -> new VehicleNotFoundException(vehicleId));
        Optional<VehicleSpec> spec = specResolutionService.resolve(vehicle);
        if (spec.isEmpty()) {
            return new CompatibleProductsResponse(PENDING, null, PageResponse.from(Page.empty(pageable)));
        }
        Page<ProductResponse> products = productRepository
                .findAll(validatedActiveProductsFor(spec.get().getId()), pageable)
                .map(ProductResponse::from);
        return new CompatibleProductsResponse(RESOLVED, VehicleSpecResponse.from(spec.get()),
                PageResponse.from(products));
    }

    // Product is the query root, so client sort properties are validated against Product fields.
    static Specification<Product> validatedActiveProductsFor(Long specId) {
        return (root, query, cb) -> {
            Subquery<Long> fitment = query.subquery(Long.class);
            Root<ProductFitment> f = fitment.from(ProductFitment.class);
            fitment.select(f.get("id")).where(
                    cb.equal(f.get("product"), root),
                    cb.equal(f.get("spec").get("id"), specId),
                    cb.equal(f.get("status"), FitmentStatus.VALIDATED));
            return cb.and(cb.isTrue(root.get("active")), cb.exists(fitment));
        };
    }
}
