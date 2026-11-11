package com.autoparts_maven.autoparts_maven.cart;

/** Requested quantity is above the available stock (409). Shared by the cart and checkout. */
public class InsufficientStockException extends RuntimeException {

    private final String sku;
    private final int available;

    public InsufficientStockException(String sku, int available) {
        super("Not enough stock for '" + sku + "': " + available + " available");
        this.sku = sku;
        this.available = available;
    }

    public String getSku() {
        return sku;
    }

    public int getAvailable() {
        return available;
    }
}
