package com.autoparts_maven.autoparts_maven.product;

public class CategoryNotFoundException extends RuntimeException {

    public CategoryNotFoundException(Long id) {
        super("Category with id %d not found".formatted(id));
    }
}
