package com.autoparts_maven.autoparts_maven.integration.vehicle;

public class VehicleLookupUnavailableException extends RuntimeException {

    public VehicleLookupUnavailableException(String message) {
        super(message);
    }

    public VehicleLookupUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
