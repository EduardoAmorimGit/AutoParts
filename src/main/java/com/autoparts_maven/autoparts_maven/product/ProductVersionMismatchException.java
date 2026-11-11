package com.autoparts_maven.autoparts_maven.product;

public class ProductVersionMismatchException extends RuntimeException {

    public ProductVersionMismatchException(Long id) {
        super("Product with id %d was modified by another request; reload it and try again".formatted(id));
    }
}
