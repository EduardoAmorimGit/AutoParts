package com.autoparts_maven.autoparts_maven.integration.vehicle;

public class VehicleLookupRateLimitedException extends RuntimeException {

    public VehicleLookupRateLimitedException(String message) {
        super(message);
    }
}
