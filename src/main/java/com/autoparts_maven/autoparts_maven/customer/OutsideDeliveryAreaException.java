package com.autoparts_maven.autoparts_maven.customer;

/** The address is outside the area the store delivers to. */
public class OutsideDeliveryAreaException extends RuntimeException {

    public OutsideDeliveryAreaException(String area) {
        super("We only deliver in " + area + " for now");
    }
}
