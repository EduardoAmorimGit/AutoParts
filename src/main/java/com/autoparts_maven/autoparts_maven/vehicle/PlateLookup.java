package com.autoparts_maven.autoparts_maven.vehicle;

import com.autoparts_maven.autoparts_maven.integration.vehicle.VehicleData;
import com.autoparts_maven.autoparts_maven.integration.vehicle.VehicleLookupProvider;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class PlateLookup {

    private final Optional<VehicleLookupProvider> provider;

    public PlateLookup(Optional<VehicleLookupProvider> provider) {
        this.provider = provider;
    }

    public VehicleData fetch(String normalizedPlate) {
        VehicleLookupProvider lookupProvider = provider.orElseThrow(() -> new VehicleLookupDisabledException(
                "Vehicle lookup provider is not configured"));
        return lookupProvider.lookupByPlate(normalizedPlate)
                .orElseThrow(() -> new VehicleNotFoundException(normalizedPlate));
    }

    public boolean isEnabled() {
        return provider.isPresent();
    }
}
