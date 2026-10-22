package com.autoparts_maven.autoparts_maven.vehicle;

import com.autoparts_maven.autoparts_maven.integration.vehicle.VehicleData;
import com.autoparts_maven.autoparts_maven.integration.vehicle.VehicleLookupProvider;
import java.util.Map;
import java.util.Optional;

public class StubVehicleLookupProvider implements VehicleLookupProvider {

    private final Map<String, VehicleData> vehicles;

    public StubVehicleLookupProvider(Map<String, VehicleData> vehicles) {
        this.vehicles = vehicles;
    }

    @Override
    public Optional<VehicleData> lookupByPlate(String plate) {
        return Optional.ofNullable(vehicles.get(plate));
    }
}
