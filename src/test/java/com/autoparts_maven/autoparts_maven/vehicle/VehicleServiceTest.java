package com.autoparts_maven.autoparts_maven.vehicle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.autoparts_maven.autoparts_maven.vehicle.dto.VehicleResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Import(VehicleLookupTestConfig.class)
@Transactional
class VehicleServiceTest {

    private static final String CLIENT = "203.0.113.10";

    @Autowired
    private VehicleService vehicleService;

    @Autowired
    private VehicleRepository vehicleRepository;

    @Test
    void lookupFetchesAndPersistsNewVehicle() {
        VehicleResponse response = vehicleService.lookupByPlate("abc1d23", CLIENT);

        assertThat(response.id()).isNotNull();
        assertThat(response.plate()).isEqualTo(VehicleLookupTestConfig.KNOWN_PLATE);
        assertThat(response.brand()).isEqualTo("VOLKSWAGEN");
        assertThat(response.model()).isEqualTo("GOL");
        assertThat(response.source()).isEqualTo("SIMPLES_API");
        assertThat(vehicleRepository.findByPlate(VehicleLookupTestConfig.KNOWN_PLATE)).isPresent();
    }

    @Test
    void lookupReusesExistingVehicleWithoutCallingProviderAgain() {
        VehicleResponse first = vehicleService.lookupByPlate(VehicleLookupTestConfig.KNOWN_PLATE, CLIENT);

        VehicleResponse second = vehicleService.lookupByPlate(VehicleLookupTestConfig.KNOWN_PLATE, CLIENT);

        assertThat(second.id()).isEqualTo(first.id());
        assertThat(vehicleRepository.count()).isEqualTo(1);
    }

    @Test
    void lookupRejectsInvalidPlate() {
        assertThatThrownBy(() -> vehicleService.lookupByPlate("invalid-plate", CLIENT))
                .isInstanceOf(InvalidPlateException.class);
    }

    @Test
    void lookupThrowsWhenProviderDoesNotFindVehicle() {
        assertThatThrownBy(() -> vehicleService.lookupByPlate(VehicleLookupTestConfig.UNKNOWN_PLATE, CLIENT))
                .isInstanceOf(VehicleNotFoundException.class)
                .hasMessageNotContaining(VehicleLookupTestConfig.UNKNOWN_PLATE);
    }

    @Test
    void storesTheRequestedNormalizedPlateEvenWhenProviderEchoesAnotherFormat() {
        VehicleResponse first = vehicleService.lookupByPlate("def-4g56", CLIENT);
        VehicleResponse second = vehicleService.lookupByPlate("DEF4G56", CLIENT);

        assertThat(first.plate()).isEqualTo(VehicleLookupTestConfig.REFORMATTED_PLATE);
        assertThat(second.id()).isEqualTo(first.id());
        assertThat(vehicleRepository.findByPlate(VehicleLookupTestConfig.REFORMATTED_PLATE)).isPresent();
        assertThat(vehicleRepository.count()).isEqualTo(1);
    }

    @Test
    void findByIdReturnsStoredVehicle() {
        VehicleResponse looked = vehicleService.lookupByPlate(VehicleLookupTestConfig.KNOWN_PLATE, CLIENT);

        assertThat(vehicleService.findById(looked.id()).plate()).isEqualTo(VehicleLookupTestConfig.KNOWN_PLATE);
        assertThatThrownBy(() -> vehicleService.findById(999_999L)).isInstanceOf(VehicleNotFoundException.class);
    }
}
