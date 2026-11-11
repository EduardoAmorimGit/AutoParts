package com.autoparts_maven.autoparts_maven.product.dto;

import com.autoparts_maven.autoparts_maven.product.Category;
import com.autoparts_maven.autoparts_maven.product.Product;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record ProductResponse(
        Long id,
        String sku,
        String name,
        String description,
        String brand,
        BigDecimal price,
        Integer stockQuantity,
        Boolean active,
        Long version,
        String type,
        CategoryResponse category,
        String viscosity,
        String oilSpecification,
        BigDecimal packageVolumeLiters,
        List<String> imageUrls,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static ProductResponse from(Product product) {
        Category category = product.getCategory();
        return new ProductResponse(
                product.getId(),
                product.getSku(),
                product.getName(),
                product.getDescription(),
                product.getBrand(),
                product.getPrice(),
                product.getStockQuantity(),
                product.getActive(),
                product.getVersion(),
                product.getType() == null ? null : product.getType().name(),
                category == null ? null : CategoryResponse.from(category),
                product.getViscosity(),
                product.getOilSpecification(),
                product.getPackageVolumeLiters(),
                List.copyOf(product.getImageUrls()),
                product.getCreatedAt(),
                product.getUpdatedAt()
        );
    }
}
