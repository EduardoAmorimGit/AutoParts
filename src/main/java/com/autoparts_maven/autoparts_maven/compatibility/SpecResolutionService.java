package com.autoparts_maven.autoparts_maven.compatibility;

import com.autoparts_maven.autoparts_maven.compatibility.CompatibilityExceptions.SpecNotFoundException;
import com.autoparts_maven.autoparts_maven.compatibility.SpecMatcher.VehicleFacts;
import com.autoparts_maven.autoparts_maven.compatibility.VehicleSpecAssignment.MatchType;
import com.autoparts_maven.autoparts_maven.vehicle.Vehicle;
import com.autoparts_maven.autoparts_maven.vehicle.VehicleNotFoundException;
import com.autoparts_maven.autoparts_maven.vehicle.VehicleRepository;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resolves a looked-up vehicle (plate) to the spec whose fitments apply to it. Order: an existing
 * assignment, an admin alias for identical provider data, then the conservative {@link SpecMatcher}.
 */
@Service
@RequiredArgsConstructor
public class SpecResolutionService {

    private final VehicleRepository vehicleRepository;
    private final VehicleSpecRepository specRepository;
    private final VehicleSpecAssignmentRepository assignmentRepository;
    private final VehicleSpecAliasRepository aliasRepository;

    /** The spec of the vehicle, resolving and remembering it when possible; empty = pending. */
    @Transactional
    public Optional<VehicleSpec> resolve(Vehicle vehicle) {
        Optional<VehicleSpecAssignment> existing = assignmentRepository.findById(vehicle.getId());
        if (existing.isPresent()) {
            return Optional.of(existing.get().getSpec());
        }
        VehicleFacts facts = factsOf(vehicle);
        Optional<VehicleSpec> spec = aliasRepository.findBySignature(facts.signature())
                .map(VehicleSpecAlias::getSpec)
                .or(() -> facts.brand() == null || facts.year() == null
                        ? Optional.empty()
                        : SpecMatcher.match(facts, specRepository.findCandidates(facts.brand(), facts.year())));
        spec.ifPresent(found -> assignmentRepository.save(
                new VehicleSpecAssignment(vehicle.getId(), found, MatchType.AUTO, Instant.now())));
        return spec;
    }

    /**
     * Admin resolution of a vehicle. Also remembers the provider data signature so every later
     * plate with identical data resolves to the same spec.
     */
    @Transactional
    public void assignManually(Long vehicleId, Long specId, Long adminId) {
        Vehicle vehicle = vehicleRepository.findById(vehicleId).orElseThrow(() -> new VehicleNotFoundException(vehicleId));
        VehicleSpec spec = specRepository.findById(specId).orElseThrow(() -> new SpecNotFoundException(specId));

        VehicleSpecAssignment assignment = assignmentRepository.findById(vehicleId)
                .orElseGet(() -> new VehicleSpecAssignment(vehicleId, spec, MatchType.MANUAL, Instant.now()));
        assignment.setSpec(spec);
        assignment.setMatchType(MatchType.MANUAL);
        assignment.setAssignedAt(Instant.now());
        assignmentRepository.save(assignment);

        String signature = factsOf(vehicle).signature();
        VehicleSpecAlias alias = aliasRepository.findBySignature(signature).orElseGet(VehicleSpecAlias::new);
        alias.setSignature(signature);
        alias.setSpec(spec);
        alias.setCreatedBy(adminId);
        aliasRepository.save(alias);
    }

    /**
     * Re-runs the automatic resolution after specs changed (import, new spec): automatic
     * assignments are recomputed, manual ones are kept. Returns how many vehicles are resolved.
     */
    @Transactional
    public int rematch() {
        assignmentRepository.deleteByMatchType(MatchType.AUTO);
        int resolved = 0;
        for (Vehicle vehicle : assignmentRepository.findAllUnassignedVehicles()) {
            if (resolve(vehicle).isPresent()) {
                resolved++;
            }
        }
        return resolved;
    }

    static VehicleFacts factsOf(Vehicle vehicle) {
        return VehicleFacts.of(vehicle.getBrand(), vehicle.getModel(), vehicle.getVersion(),
                vehicle.getManufactureYear(), vehicle.getModelYear(), vehicle.getEngineCc(), vehicle.getFuel());
    }
}
