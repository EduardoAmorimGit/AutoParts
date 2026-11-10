package com.autoparts_maven.autoparts_maven.compatibility;

import com.autoparts_maven.autoparts_maven.product.Product;
import com.autoparts_maven.autoparts_maven.vehicle.Vehicle;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

/**
 * Queries over the explicit {@code product_vehicle} links. The target entity (vehicle or product)
 * is the query root, so client-supplied sort properties are validated against that entity's fields
 * and a page is fetched in one query instead of one lazy load per link.
 */
final class CompatibilitySpecifications {

    private CompatibilitySpecifications() {
    }

    static Specification<Vehicle> vehiclesLinkedTo(Long productId) {
        return (root, query, cb) -> {
            Subquery<Long> link = query.subquery(Long.class);
            Root<ProductVehicle> pv = link.from(ProductVehicle.class);
            link.select(pv.get("id")).where(
                    cb.equal(pv.get("vehicle"), root),
                    cb.equal(pv.get("product").get("id"), productId));
            return cb.exists(link);
        };
    }

    /** Only active products with a registered link: customers never see unconfirmed compatibility. */
    static Specification<Product> activeProductsLinkedTo(Long vehicleId) {
        return (root, query, cb) -> {
            Subquery<Long> link = query.subquery(Long.class);
            Root<ProductVehicle> pv = link.from(ProductVehicle.class);
            link.select(pv.get("id")).where(
                    cb.equal(pv.get("product"), root),
                    cb.equal(pv.get("vehicle").get("id"), vehicleId));
            return cb.and(cb.isTrue(root.get("active")), cb.exists(link));
        };
    }
}
