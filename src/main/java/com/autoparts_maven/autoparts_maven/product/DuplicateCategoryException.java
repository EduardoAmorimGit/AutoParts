package com.autoparts_maven.autoparts_maven.product;

public class DuplicateCategoryException extends RuntimeException {

    public DuplicateCategoryException(String name) {
        super("A category named '%s' already exists".formatted(name));
    }
}
