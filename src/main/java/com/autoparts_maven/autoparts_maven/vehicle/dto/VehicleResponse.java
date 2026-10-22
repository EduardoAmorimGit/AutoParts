package com.autoparts_maven.autoparts_maven.vehicle.dto;

import com.autoparts_maven.autoparts_maven.vehicle.Vehicle;
import java.time.LocalDateTime;

public record VehicleResponse(
        Long id,
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
        String origin,
        String source,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static VehicleResponse from(Vehicle vehicle) {
        return new VehicleResponse(
                vehicle.getId(),
                vehicle.getPlate(),
                vehicle.getBrand(),
                vehicle.getModel(),
                vehicle.getVersion(),
                vehicle.getManufactureYear(),
                vehicle.getModelYear(),
                vehicle.getFuel(),
                vehicle.getVehicleType(),
                vehicle.getSpecies(),
                vehicle.getPassengerCapacity(),
                vehicle.getEngineCc(),
                vehicle.getOrigin(),
                vehicle.getSource(),
                vehicle.getCreatedAt(),
                vehicle.getUpdatedAt()
        );
    }
}
