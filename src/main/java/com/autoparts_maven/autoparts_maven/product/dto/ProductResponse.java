package com.autoparts_maven.autoparts_maven.product.dto;

import com.autoparts_maven.autoparts_maven.product.Product;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ProductResponse(
        Long id,
        String sku,
        String name,
        String description,
        String brand,
        BigDecimal price,
        Integer stockQuantity,
        Boolean active,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static ProductResponse from(Product product) {
        return new ProductResponse(
                product.getId(),
                product.getSku(),
                product.getName(),
                product.getDescription(),
                product.getBrand(),
                product.getPrice(),
                product.getStockQuantity(),
                product.getActive(),
                product.getCreatedAt(),
                product.getUpdatedAt()
        );
    }
}
