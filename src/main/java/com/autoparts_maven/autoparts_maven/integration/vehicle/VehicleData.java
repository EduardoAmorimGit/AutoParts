package com.autoparts_maven.autoparts_maven.integration.vehicle;

public record VehicleData(
        String plate,
        String brand,
        String model,
        String version,
        Integer manufactureYear,
        Integer modelYear,
        String fuel,
        String vehicleType,
        String species,
        Integer passengerCapacity,
        Integer engineCc,
        String origin
) {
}
