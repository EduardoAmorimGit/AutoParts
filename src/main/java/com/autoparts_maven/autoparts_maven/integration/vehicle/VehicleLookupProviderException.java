package com.autoparts_maven.autoparts_maven.integration.vehicle;

public class VehicleLookupProviderException extends RuntimeException {

    public VehicleLookupProviderException(String message) {
        super(message);
    }

    public VehicleLookupProviderException(String message, Throwable cause) {
        super(message, cause);
    }
}
