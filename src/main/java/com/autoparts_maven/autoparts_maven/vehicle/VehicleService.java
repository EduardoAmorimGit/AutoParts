package com.autoparts_maven.autoparts_maven.vehicle;

import com.autoparts_maven.autoparts_maven.common.ratelimit.FixedWindowRateLimiter;
import com.autoparts_maven.autoparts_maven.integration.vehicle.VehicleData;
import com.autoparts_maven.autoparts_maven.vehicle.dto.VehicleResponse;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VehicleService {

    private final VehicleRepository vehicleRepository;
    private final PlateLookup plateLookup;
    private final FixedWindowRateLimiter lookupRateLimiter;

    public VehicleService(VehicleRepository vehicleRepository, PlateLookup plateLookup,
            @Qualifier("lookupRateLimiter") FixedWindowRateLimiter lookupRateLimiter) {
        this.vehicleRepository = vehicleRepository;
        this.plateLookup = plateLookup;
        this.lookupRateLimiter = lookupRateLimiter;
    }

    /**
     * Resolves a plate to a vehicle, reusing the stored record when present.
     *
     * <p>Deliberately not {@code @Transactional}: the provider call can take seconds and must not
     * hold a pooled database connection. Each repository call runs in its own short transaction.
     * Only provider calls count against the caller's rate limit; stored vehicles are served freely.
     */
    public VehicleResponse lookupByPlate(String rawPlate, String clientId) {
        String plate = normalizeOrThrow(rawPlate);

        Optional<Vehicle> existing = vehicleRepository.findByPlate(plate);
        if (existing.isPresent()) {
            return VehicleResponse.from(existing.get());
        }

        lookupRateLimiter.acquire(clientId);
        VehicleData data = plateLookup.fetch(plate);
        return VehicleResponse.from(persist(plate, data));
    }

    @Transactional(readOnly = true)
    public VehicleResponse findById(Long id) {
        return vehicleRepository.findById(id)
                .map(VehicleResponse::from)
                .orElseThrow(() -> new VehicleNotFoundException(id));
    }

    private String normalizeOrThrow(String rawPlate) {
        String plate = PlateNormalizer.normalize(rawPlate);
        if (!PlateNormalizer.isValid(plate)) {
            throw new InvalidPlateException("Plate format is invalid");
        }
        return plate;
    }

    // The stored plate is always the normalized plate that was requested, never the one echoed by
    // the provider, so the unique key matches future lookups. A concurrent lookup of the same plate
    // may win the insert; in that case the row it stored is returned.
    private Vehicle persist(String plate, VehicleData data) {
        Vehicle vehicle = new Vehicle();
        vehicle.setPlate(plate);
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
        vehicle.setSource(VehicleSource.SIMPLES_API);
        try {
            return vehicleRepository.saveAndFlush(vehicle);
        } catch (DataIntegrityViolationException exception) {
            return vehicleRepository.findByPlate(plate).orElseThrow(() -> exception);
        }
    }
}
