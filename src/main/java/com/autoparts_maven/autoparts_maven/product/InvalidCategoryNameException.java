package com.autoparts_maven.autoparts_maven.product;

public class InvalidCategoryNameException extends RuntimeException {

    public InvalidCategoryNameException() {
        super("Category name must contain letters or digits");
    }
}
