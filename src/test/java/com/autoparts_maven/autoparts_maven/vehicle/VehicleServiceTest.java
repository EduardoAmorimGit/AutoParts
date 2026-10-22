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

    @Autowired
    private VehicleService vehicleService;

    @Autowired
    private VehicleRepository vehicleRepository;

    @Test
    void lookupFetchesAndPersistsNewVehicle() {
        VehicleResponse response = vehicleService.lookupByPlate("abc1d23");

        assertThat(response.id()).isNotNull();
        assertThat(response.plate()).isEqualTo(VehicleLookupTestConfig.KNOWN_PLATE);
        assertThat(response.brand()).isEqualTo("VOLKSWAGEN");
        assertThat(response.model()).isEqualTo("GOL");
        assertThat(response.source()).isEqualTo("SIMPLES_API");
        assertThat(vehicleRepository.findByPlate(VehicleLookupTestConfig.KNOWN_PLATE)).isPresent();
    }

    @Test
    void lookupReusesExistingVehicleWithoutCallingProviderAgain() {
        VehicleResponse first = vehicleService.lookupByPlate(VehicleLookupTestConfig.KNOWN_PLATE);

        VehicleResponse second = vehicleService.lookupByPlate(VehicleLookupTestConfig.KNOWN_PLATE);

        assertThat(second.id()).isEqualTo(first.id());
        assertThat(vehicleRepository.count()).isEqualTo(1);
    }

    @Test
    void lookupRejectsInvalidPlate() {
        assertThatThrownBy(() -> vehicleService.lookupByPlate("invalid-plate"))
                .isInstanceOf(InvalidPlateException.class);
    }

    @Test
    void lookupThrowsWhenProviderDoesNotFindVehicle() {
        assertThatThrownBy(() -> vehicleService.lookupByPlate(VehicleLookupTestConfig.UNKNOWN_PLATE))
                .isInstanceOf(VehicleNotFoundException.class);
    }
}
