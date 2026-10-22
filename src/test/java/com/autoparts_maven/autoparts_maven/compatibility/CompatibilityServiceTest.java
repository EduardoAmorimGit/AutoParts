package com.autoparts_maven.autoparts_maven.compatibility;

import static org.assertj.core.api.Assertions.assertThat;

import com.autoparts_maven.autoparts_maven.product.ProductRepository;
import com.autoparts_maven.autoparts_maven.product.ProductService;
import com.autoparts_maven.autoparts_maven.product.dto.ProductRequest;
import com.autoparts_maven.autoparts_maven.product.dto.ProductResponse;
import com.autoparts_maven.autoparts_maven.vehicle.Vehicle;
import com.autoparts_maven.autoparts_maven.vehicle.VehicleRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
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

    private Vehicle seedVehicle(String plate) {
        Vehicle vehicle = new Vehicle();
        vehicle.setPlate(plate);
        vehicle.setBrand("FORD");
        vehicle.setModel("KA");
        vehicle.setManufactureYear(2019);
        vehicle.setSource("SIMPLES_API");
        LocalDateTime now = LocalDateTime.now();
        vehicle.setCreatedAt(now);
        vehicle.setUpdatedAt(now);
        return vehicleRepository.save(vehicle);
    }
}
