package com.autoparts_maven.autoparts_maven.compatibility;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.autoparts_maven.autoparts_maven.product.ProductRepository;
import com.autoparts_maven.autoparts_maven.product.ProductService;
import com.autoparts_maven.autoparts_maven.product.dto.ProductRequest;
import com.autoparts_maven.autoparts_maven.product.dto.ProductResponse;
import com.autoparts_maven.autoparts_maven.vehicle.Vehicle;
import com.autoparts_maven.autoparts_maven.product.ProductNotFoundException;
import com.autoparts_maven.autoparts_maven.vehicle.VehicleRepository;
import com.autoparts_maven.autoparts_maven.vehicle.VehicleSource;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.core.PropertyReferenceException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CompatibilityServiceTest {

    @Autowired
    private CompatibilityService compatibilityService;

    @Autowired
    private ProductService productService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private VehicleRepository vehicleRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    void linksProductAndVehicleAndReadsBothDirections() {
        ProductResponse product = productService.create(
                new ProductRequest("SKU-COMP-1", "Brake Pad", null, null, new BigDecimal("99.90"), 5, true));
        Vehicle vehicle = seedVehicle("ABC1D23");

        compatibilityService.link(product.id(), vehicle.getId());

        var vehicles = compatibilityService.findVehiclesForProduct(product.id(), PageRequest.of(0, 20));
        var products = compatibilityService.findProductsForVehicle(vehicle.getId(), PageRequest.of(0, 20));

        assertThat(vehicles.content()).extracting("id").containsExactly(vehicle.getId());
        assertThat(products.content()).extracting("id").containsExactly(product.id());
        assertThat(vehicles.totalElements()).isEqualTo(1);
        assertThat(products.totalElements()).isEqualTo(1);
    }

    @Test
    void linkingTwiceIsIdempotent() {
        ProductResponse product = productService.create(
                new ProductRequest("SKU-COMP-2", "Oil Filter", null, null, new BigDecimal("49.90"), 5, true));
        Vehicle vehicle = seedVehicle("ABC1234");

        compatibilityService.link(product.id(), vehicle.getId());
        compatibilityService.link(product.id(), vehicle.getId());

        assertThat(compatibilityService.findVehiclesForProduct(product.id(), PageRequest.of(0, 20)).totalElements())
                .isEqualTo(1);
    }

    @Test
    void unlinkRemovesCompatibility() {
        ProductResponse product = productService.create(
                new ProductRequest("SKU-COMP-3", "Spark Plug", null, null, new BigDecimal("19.90"), 5, true));
        Vehicle vehicle = seedVehicle("DEF1G23");

        compatibilityService.link(product.id(), vehicle.getId());
        compatibilityService.unlink(product.id(), vehicle.getId());

        assertThat(compatibilityService.findVehiclesForProduct(product.id(), PageRequest.of(0, 20)).totalElements())
                .isZero();
    }

    @Test
    void deletingProductWithLinksIsHandledByDatabaseCascade() {
        ProductResponse product = productService.create(
                new ProductRequest("SKU-COMP-4", "Cascade", null, null, new BigDecimal("9.90"), 5, true));
        Vehicle vehicle = seedVehicle("GHI1J23");

        compatibilityService.link(product.id(), vehicle.getId());
        productRepository.flush();
        entityManager.clear();

        assertThat(countLinks(product.id())).isEqualTo(1);

        productRepository.deleteById(product.id());
        productRepository.flush();
        entityManager.clear();

        assertThat(countLinks(product.id())).isZero();
    }

    private long countLinks(Long productId) {
        Number count = (Number) entityManager
                .createNativeQuery("SELECT COUNT(*) FROM product_vehicle WHERE product_id = :id")
                .setParameter("id", productId)
                .getSingleResult();
        return count.longValue();
    }

    @Test
    void customerViewHidesInactiveProducts() {
        ProductResponse active = productService.create(
                new ProductRequest("SKU-COMP-5", "Active", null, null, new BigDecimal("1.00"), 1, true));
        ProductResponse inactive = productService.create(
                new ProductRequest("SKU-COMP-6", "Inactive", null, null, new BigDecimal("1.00"), 1, false));
        Vehicle vehicle = seedVehicle("JKL1M23");

        compatibilityService.link(active.id(), vehicle.getId());
        compatibilityService.link(inactive.id(), vehicle.getId());

        var products = compatibilityService.findProductsForVehicle(vehicle.getId(), PageRequest.of(0, 20));

        assertThat(products.content()).extracting("id").containsExactly(active.id());
        assertThat(products.totalElements()).isEqualTo(1);
    }

    @Test
    void unlinkedProductsAreNeverReturnedForAVehicle() {
        productService.create(new ProductRequest("SKU-COMP-7", "Unlinked", null, null, new BigDecimal("1.00"), 1, true));
        Vehicle vehicle = seedVehicle("MNO1P23");

        assertThat(compatibilityService.findProductsForVehicle(vehicle.getId(), PageRequest.of(0, 20)).content())
                .isEmpty();
    }

    @Test
    void sortsByTheTargetEntityFields() {
        ProductResponse product = productService.create(
                new ProductRequest("SKU-COMP-8", "Sorted", null, null, new BigDecimal("1.00"), 1, true));
        Vehicle ford = seedVehicle("PQR1S23", "FORD");
        Vehicle audi = seedVehicle("STU1V23", "AUDI");
        compatibilityService.link(product.id(), ford.getId());
        compatibilityService.link(product.id(), audi.getId());

        var vehicles = compatibilityService.findVehiclesForProduct(product.id(),
                PageRequest.of(0, 20, Sort.by("brand")));

        assertThat(vehicles.content()).extracting("brand").containsExactly("AUDI", "FORD");
    }

    @Test
    void rejectsUnknownSortProperty() {
        Vehicle vehicle = seedVehicle("VWX1Y23");

        assertThatThrownBy(() -> compatibilityService.findProductsForVehicle(vehicle.getId(),
                PageRequest.of(0, 20, Sort.by("bogus"))))
                .isInstanceOf(PropertyReferenceException.class);
    }

    @Test
    void linkFailsForUnknownProduct() {
        Vehicle vehicle = seedVehicle("YZA1B23");

        assertThatThrownBy(() -> compatibilityService.link(999_999L, vehicle.getId()))
                .isInstanceOf(ProductNotFoundException.class);
    }

    private Vehicle seedVehicle(String plate) {
        return seedVehicle(plate, "FORD");
    }

    private Vehicle seedVehicle(String plate, String brand) {
        Vehicle vehicle = new Vehicle();
        vehicle.setPlate(plate);
        vehicle.setBrand(brand);
        vehicle.setModel("KA");
        vehicle.setManufactureYear(2019);
        vehicle.setSource(VehicleSource.SIMPLES_API);
        return vehicleRepository.save(vehicle);
    }
}
