package com.autoparts_maven.autoparts_maven.product;

public class ProductNotFoundException extends RuntimeException {

    public ProductNotFoundException(Long id) {
        super("Product with id %d not found".formatted(id));
    }
}
