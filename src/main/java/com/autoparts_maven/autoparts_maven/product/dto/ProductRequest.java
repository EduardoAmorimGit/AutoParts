package com.autoparts_maven.autoparts_maven.product.dto;

import com.autoparts_maven.autoparts_maven.product.ProductType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

public record ProductRequest(

        @NotBlank
        @Size(max = 50)
        String sku,

        @NotBlank
        @Size(max = 150)
        String name,

        @Size(max = 2000)
        String description,

        @Size(max = 100)
        String brand,

        @NotNull
        @DecimalMin(value = "0.00")
        @Digits(integer = 10, fraction = 2)
        BigDecimal price,

        @NotNull
        @PositiveOrZero
        Integer stockQuantity,

        Boolean active,

        /** Optional; defaults to OTHER on create and keeps the current value on update. */
        ProductType type,

        Long categoryId,

        @Size(max = 20)
        String viscosity,

        @Size(max = 100)
        String oilSpecification,

        @DecimalMin(value = "0.01")
        @Digits(integer = 3, fraction = 2)
        BigDecimal packageVolumeLiters,

        @Size(max = 10)
        List<@NotBlank @Size(max = 500) @Pattern(regexp = "^https://\\S+$", message = "must be an https URL") String> imageUrls
) {

    /** Basic product data only (catalog attributes left empty). */
    public ProductRequest(String sku, String name, String description, String brand, BigDecimal price,
            Integer stockQuantity, Boolean active) {
        this(sku, name, description, brand, price, stockQuantity, active, null, null, null, null, null, null);
    }
}
