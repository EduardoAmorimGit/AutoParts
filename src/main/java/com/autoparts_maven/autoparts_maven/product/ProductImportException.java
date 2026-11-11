package com.autoparts_maven.autoparts_maven.product;

/** The product spreadsheet as a whole cannot be processed (unreadable, empty, missing columns). */
public class ProductImportException extends RuntimeException {

    public ProductImportException(String message) {
        super(message);
    }
}
