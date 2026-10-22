package com.autoparts_maven.autoparts_maven.vehicle;

import com.autoparts_maven.autoparts_maven.integration.vehicle.VehicleData;
import com.autoparts_maven.autoparts_maven.vehicle.dto.VehicleResponse;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class VehicleService {

    private final VehicleRepository vehicleRepository;
    private final PlateLookup plateLookup;

    @Transactional
    public VehicleResponse lookupByPlate(String rawPlate) {
        String plate = normalizeOrThrow(rawPlate);

        return vehicleRepository.findByPlate(plate)
                .map(VehicleResponse::from)
                .orElseGet(() -> VehicleResponse.from(persist(plateLookup.fetch(plate))));
    }

    @Transactional(readOnly = true)
    public Vehicle getByIdOrThrow(Long id) {
        return vehicleRepository.findById(id)
                .orElseThrow(() -> new VehicleNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public Vehicle getByPlateOrThrow(String rawPlate) {
        String plate = normalizeOrThrow(rawPlate);
        return vehicleRepository.findByPlate(plate)
                .orElseThrow(() -> new VehicleNotFoundException(plate));
    }

    public boolean isLookupEnabled() {
        return plateLookup.isEnabled();
    }

    private String normalizeOrThrow(String rawPlate) {
        String plate = PlateNormalizer.normalize(rawPlate);
        if (!PlateNormalizer.isValid(plate)) {
            throw new InvalidPlateException("Plate format is invalid");
        }
        return plate;
    }

    private Vehicle persist(VehicleData data) {
        Vehicle vehicle = new Vehicle();
        vehicle.setPlate(data.plate());
        vehicle.setBrand(data.brand());
        vehicle.setModel(data.model());
        vehicle.setVersion(data.version());
        vehicle.setManufactureYear(data.manufactureYear());
        vehicle.setModelYear(data.modelYear());
        vehicle.setFuel(data.fuel());
        vehicle.setVehicleType(data.vehicleType());
        vehicle.setSpecies(data.species());
        vehicle.setPassengerCapacity(data.passengerCapacity());
        vehicle.setEngineCc(data.engineCc());
        vehicle.setOrigin(data.origin());
        vehicle.setSource("SIMPLES_API");
        LocalDateTime now = LocalDateTime.now();
        vehicle.setCreatedAt(now);
        vehicle.setUpdatedAt(now);
        return vehicleRepository.save(vehicle);
    }
}
