package com.autoparts_maven.autoparts_maven.vehicle;

public class VehicleNotFoundException extends RuntimeException {

    public VehicleNotFoundException(Long id) {
        super("Vehicle with id " + id + " not found");
    }

    private VehicleNotFoundException(String message) {
        super(message);
    }

    /** The plate is personal data (LGPD), so it is never echoed back in the error message. */
    public static VehicleNotFoundException forPlate() {
        return new VehicleNotFoundException("No vehicle found for the given plate");
    }
}
