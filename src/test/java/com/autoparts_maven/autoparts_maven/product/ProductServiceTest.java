package com.autoparts_maven.autoparts_maven.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.autoparts_maven.autoparts_maven.product.dto.ProductRequest;
import com.autoparts_maven.autoparts_maven.product.dto.ProductResponse;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ProductServiceTest {

    @Autowired
    private ProductService productService;

    @Test
    void createsProductWithDefaults() {
        ProductResponse created = productService.create(request("SKU-100", "Oil Filter", true));

        assertThat(created.id()).isNotNull();
        assertThat(created.sku()).isEqualTo("SKU-100");
        assertThat(created.name()).isEqualTo("Oil Filter");
        assertThat(created.createdAt()).isNotNull();
        assertThat(created.updatedAt()).isNotNull();
    }

    @Test
    void defaultsActiveToTrueWhenNull() {
        ProductResponse created = productService.create(
                new ProductRequest("SKU-101", "Air Filter", null, null, new BigDecimal("25.00"), 5, null));

        assertThat(created.active()).isTrue();
    }

    @Test
    void rejectsDuplicateSku() {
        productService.create(request("SKU-200", "Spark Plug", true));

        assertThatThrownBy(() -> productService.create(request("SKU-200", "Other", true)))
                .isInstanceOf(DuplicateSkuException.class);
    }

    @Test
    void updatesProduct() {
        ProductResponse created = productService.create(request("SKU-300", "Wiper", true));

        ProductResponse updated = productService.update(created.id(),
                request("SKU-300", "Wiper Blade", false));

        assertThat(updated.name()).isEqualTo("Wiper Blade");
        assertThat(updated.active()).isFalse();
    }

    @Test
    void deletesProduct() {
        ProductResponse created = productService.create(request("SKU-400", "Battery", true));

        productService.delete(created.id());

        assertThatThrownBy(() -> productService.findById(created.id()))
                .isInstanceOf(ProductNotFoundException.class);
    }

    @Test
    void failsWhenProductNotFound() {
        assertThatThrownBy(() -> productService.findById(999999L))
                .isInstanceOf(ProductNotFoundException.class);
    }

    @Test
    void normalizesSkuWithTrimAndUppercase() {
        ProductResponse created = productService.create(
                new ProductRequest("  ab-12  ", "Normalized", null, null, new BigDecimal("1.00"), 1, true));

        assertThat(created.sku()).isEqualTo("AB-12");
    }

    @Test
    void rejectsDuplicateSkuIgnoringCaseAndSpaces() {
        productService.create(new ProductRequest("ab-99", "First", null, null, new BigDecimal("1.00"), 1, true));

        assertThatThrownBy(() -> productService.create(
                new ProductRequest("  AB-99 ", "Second", null, null, new BigDecimal("1.00"), 1, true)))
                .isInstanceOf(DuplicateSkuException.class);
    }

    @Test
    void normalizesSkuOnUpdate() {
        ProductResponse created = productService.create(
                new ProductRequest("upd-1", "Before", null, null, new BigDecimal("1.00"), 1, true));

        ProductResponse updated = productService.update(created.id(),
                new ProductRequest(" upd-2 ", "After", null, null, new BigDecimal("1.00"), 1, true));

        assertThat(updated.sku()).isEqualTo("UPD-2");
    }

    private ProductRequest request(String sku, String name, Boolean active) {
        return new ProductRequest(sku, name, "desc", "brand", new BigDecimal("10.00"), 1, active);
    }
}
