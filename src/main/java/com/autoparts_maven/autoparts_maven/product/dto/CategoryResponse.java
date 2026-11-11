package com.autoparts_maven.autoparts_maven.product.dto;

import com.autoparts_maven.autoparts_maven.product.Category;

public record CategoryResponse(Long id, String name, String slug) {

    public static CategoryResponse from(Category category) {
        return new CategoryResponse(category.getId(), category.getName(), category.getSlug());
    }
}
