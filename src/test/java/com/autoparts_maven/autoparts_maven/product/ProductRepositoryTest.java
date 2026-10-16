package com.autoparts_maven.autoparts_maven.product;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ProductRepositoryTest {

    @Autowired
    private ProductRepository productRepository;

    @Test
    void savesAndFindsProduct() {
        Product product = new Product();
        product.setSku("SKU-001");
        product.setName("Brake Pad");
        product.setPrice(new BigDecimal("99.90"));
        product.setStockQuantity(10);
        product.setActive(true);
        product.setCreatedAt(LocalDateTime.now());
        product.setUpdatedAt(LocalDateTime.now());

        Product saved = productRepository.save(product);

        assertThat(saved.getId()).isNotNull();
        assertThat(productRepository.findById(saved.getId())).isPresent();
    }
}
