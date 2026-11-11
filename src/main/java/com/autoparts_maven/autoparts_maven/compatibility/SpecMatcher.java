package com.autoparts_maven.autoparts_maven.compatibility;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Decides which spec a looked-up vehicle is, using only data the provider returned.
 *
 * <p>Deliberately conservative: a spec is a candidate only if brand, model-year range, model name
 * and every engine/fuel detail the spec states are consistent with the vehicle. The result is a
 * match only when exactly one most specific candidate remains; anything ambiguous or unknown is
 * left for an administrator, so a customer is never shown parts of a guessed model.
 */
public final class SpecMatcher {

    /** Maximum difference between provider cc (999) and catalog displacement ("1.0" = 1000). */
    private static final int DISPLACEMENT_TOLERANCE_CC = 100;

    private SpecMatcher() {
    }

    public record VehicleFacts(String brand, String model, String version, Integer year, Integer engineCc,
            FuelType fuel) {

        /** Normalizes raw provider fields; the model year is preferred over the manufacture year. */
        public static VehicleFacts of(String brand, String model, String version, Integer manufactureYear,
                Integer modelYear, Integer engineCc, String fuel) {
            String normalizedBrand = VehicleNames.brand(brand);
            return new VehicleFacts(
                    normalizedBrand,
                    VehicleNames.model(model, normalizedBrand),
                    VehicleNames.text(version),
                    modelYear != null ? modelYear : manufactureYear,
                    engineCc,
                    FuelType.parse(fuel).orElse(null));
        }

        /** Stable key of the provider data, used to remember an admin's manual resolution. */
        public String signature() {
            return String.join("|", String.valueOf(brand), String.valueOf(model), String.valueOf(version),
                    String.valueOf(year), String.valueOf(engineCc), String.valueOf(fuel));
        }

        String fullText() {
            return version == null ? model : model + " " + version;
        }
    }

    public static Optional<VehicleSpec> match(VehicleFacts vehicle, List<VehicleSpec> specs) {
        if (vehicle.brand() == null || vehicle.model() == null || vehicle.year() == null) {
            return Optional.empty();
        }
        List<VehicleSpec> candidates = new ArrayList<>();
        for (VehicleSpec spec : specs) {
            if (isCandidate(vehicle, spec)) {
                candidates.add(spec);
            }
        }
        if (candidates.isEmpty()) {
            return Optional.empty();
        }

        // Prefer the spec naming the model most precisely ("GOL G5" over "GOL"), then the one that
        // states the most details; a tie at the top means the data cannot tell them apart.
        Comparator<VehicleSpec> specificity = Comparator
                .comparingInt((VehicleSpec spec) -> VehicleNames.tokens(spec.getModel()).size())
                .thenComparingInt(SpecMatcher::detailCount);
        candidates.sort(specificity.reversed());
        if (candidates.size() > 1 && specificity.compare(candidates.get(0), candidates.get(1)) == 0) {
            return Optional.empty();
        }
        return Optional.of(candidates.get(0));
    }

    private static boolean isCandidate(VehicleFacts vehicle, VehicleSpec spec) {
        if (!vehicle.brand().equals(spec.getBrand())) {
            return false;
        }
        if (vehicle.year() < spec.getYearStart() || vehicle.year() > spec.getYearEnd()) {
            return false;
        }
        List<String> vehicleTokens = VehicleNames.tokens(vehicle.fullText());
        if (!VehicleNames.startsWithTokens(VehicleNames.tokens(vehicle.model()), VehicleNames.tokens(spec.getModel()))) {
            return false;
        }
        if (spec.getVersion() != null
                && !VehicleNames.containsAllTokens(vehicleTokens, VehicleNames.tokens(spec.getVersion()))) {
            return false;
        }
        if (spec.getFuel() != null && vehicle.fuel() != null && spec.getFuel() != vehicle.fuel()) {
            return false;
        }
        return engineCompatible(vehicle, spec);
    }

    private static boolean engineCompatible(VehicleFacts vehicle, VehicleSpec spec) {
        if (spec.getEngine() == null) {
            return true;
        }
        Optional<Integer> specCc = VehicleNames.displacementCc(spec.getEngine());
        Integer vehicleCc = vehicle.engineCc() != null
                ? vehicle.engineCc()
                : VehicleNames.displacementCc(vehicle.fullText()).orElse(null);
        if (specCc.isPresent() && vehicleCc != null
                && Math.abs(specCc.get() - vehicleCc) > DISPLACEMENT_TOLERANCE_CC) {
            return false;
        }
        Optional<String> specValves = VehicleNames.valves(spec.getEngine());
        Optional<String> vehicleValves = VehicleNames.valves(vehicle.fullText());
        return specValves.isEmpty() || vehicleValves.isEmpty() || specValves.equals(vehicleValves);
    }

    private static int detailCount(VehicleSpec spec) {
        int count = 0;
        if (spec.getVersion() != null) {
            count++;
        }
        if (spec.getEngine() != null) {
            count++;
        }
        if (spec.getFuel() != null) {
            count++;
        }
        return count;
    }
}
