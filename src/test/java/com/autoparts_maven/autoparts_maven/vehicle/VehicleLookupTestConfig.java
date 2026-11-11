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
    /** The provider echoes this plate in another format ("DEF-4G56"). */
    public static final String REFORMATTED_PLATE = "DEF4G56";
    /** Two different plates whose provider data is identical (same model, year and engine). */
    public static final String TWIN_PLATE_A = "ONX1A23";
    public static final String TWIN_PLATE_B = "ONX2B34";

    @Bean
    @Primary
    VehicleLookupProvider stubVehicleLookupProvider() {
        return new StubVehicleLookupProvider(Map.of(
                KNOWN_PLATE, new VehicleData(
                        KNOWN_PLATE, "VOLKSWAGEN", "GOL", "1.0 MPI", 2020, 2021,
                        "FLEX", "AUTOMOVEL", "PASSAGEIRO", 5, 1000, "NACIONAL"),
                REFORMATTED_PLATE, new VehicleData(
                        "DEF-4G56", "FIAT", "UNO", null, 2015, 2015,
                        null, null, null, null, null, null),
                TWIN_PLATE_A, twin(TWIN_PLATE_A),
                TWIN_PLATE_B, twin(TWIN_PLATE_B)));
    }

    private static VehicleData twin(String plate) {
        return new VehicleData(plate, "GM", "CHEV/ONIX 10MT JOYE", null, 2019, 2019,
                "ALCOOL/GASOLINA", "AUTOMOVEL", "PASSAGEIRO", 5, 999, "NACIONAL");
    }
}
