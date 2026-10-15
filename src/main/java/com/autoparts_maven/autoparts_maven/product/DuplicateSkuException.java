package com.autoparts_maven.autoparts_maven.product;

public class DuplicateSkuException extends RuntimeException {

    public DuplicateSkuException(String sku) {
        super("A product with SKU '%s' already exists".formatted(sku));
    }
}
