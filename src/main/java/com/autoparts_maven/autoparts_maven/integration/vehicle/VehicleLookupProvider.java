package com.autoparts_maven.autoparts_maven.integration.vehicle;

import java.util.Optional;

public interface VehicleLookupProvider {

    Optional<VehicleData> lookupByPlate(String plate);
}
