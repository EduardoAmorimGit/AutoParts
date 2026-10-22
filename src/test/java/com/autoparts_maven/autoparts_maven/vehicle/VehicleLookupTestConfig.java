package com.autoparts_maven.autoparts_maven.vehicle;

import com.autoparts_maven.autoparts_maven.integration.vehicle.VehicleData;
import com.autoparts_maven.autoparts_maven.integration.vehicle.VehicleLookupProvider;
import java.util.Map;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration
public class VehicleLookupTestConfig {

    public static final String KNOWN_PLATE = "ABC1D23";
    public static final String UNKNOWN_PLATE = "ZZZ9Z99";

    @Bean
    @Primary
    VehicleLookupProvider stubVehicleLookupProvider() {
        return new StubVehicleLookupProvider(Map.of(
                KNOWN_PLATE, new VehicleData(
                        KNOWN_PLATE, "VOLKSWAGEN", "GOL", "1.0 MPI", 2020, 2021,
                        "FLEX", "AUTOMOVEL", "PASSAGEIRO", 5, 1000, "NACIONAL")));
    }
}
