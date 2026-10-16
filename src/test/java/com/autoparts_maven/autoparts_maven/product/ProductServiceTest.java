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

    private ProductRequest request(String sku, String name, Boolean active) {
        return new ProductRequest(sku, name, "desc", "brand", new BigDecimal("10.00"), 1, active);
    }
}
