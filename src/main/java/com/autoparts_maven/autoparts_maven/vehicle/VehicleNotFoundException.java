package com.autoparts_maven.autoparts_maven.vehicle;

public class VehicleNotFoundException extends RuntimeException {

    public VehicleNotFoundException(Long id) {
        super("Vehicle with id " + id + " not found");
    }

    public VehicleNotFoundException(String plate) {
        super("Vehicle with plate " + plate + " not found");
    }
}
