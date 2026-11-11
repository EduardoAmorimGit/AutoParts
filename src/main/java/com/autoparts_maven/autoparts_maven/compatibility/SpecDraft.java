package com.autoparts_maven.autoparts_maven.compatibility;

import com.autoparts_maven.autoparts_maven.compatibility.CompatibilityExceptions.InvalidSpecException;
import java.math.BigDecimal;

/** Normalized, validated spec fields coming from the admin API or an import row. */
record SpecDraft(String brand, String model, String version, String engine, FuelType fuel, int yearStart,
        int yearEnd, BigDecimal oilCapacityLiters) {

    private static final BigDecimal MAX_OIL_LITERS = new BigDecimal("99.99");

    static SpecDraft of(String brand, String model, String version, String engine, String fuel, Integer yearStart,
            Integer yearEnd, BigDecimal rawOilCapacityLiters) {
        // "4.000" from a spreadsheet is 4 liters, not a value with too many decimals.
        BigDecimal oilCapacityLiters = rawOilCapacityLiters == null ? null : rawOilCapacityLiters.stripTrailingZeros();
        String normalizedBrand = VehicleNames.brand(brand);
        String normalizedModel = VehicleNames.model(model, normalizedBrand);
        if (normalizedBrand == null || normalizedModel == null) {
            throw new InvalidSpecException("Brand and model are required");
        }
        if (yearStart == null || yearEnd == null || yearStart > yearEnd || yearStart < 1950 || yearEnd > 2100) {
            throw new InvalidSpecException("Year range must be between 1950 and 2100, start <= end");
        }
        FuelType fuelType = null;
        if (VehicleNames.text(fuel) != null) {
            fuelType = FuelType.parse(fuel)
                    .orElseThrow(() -> new InvalidSpecException("Unknown fuel '" + fuel + "'"));
        }
        if (oilCapacityLiters != null && (oilCapacityLiters.signum() <= 0
                || oilCapacityLiters.compareTo(MAX_OIL_LITERS) > 0 || oilCapacityLiters.scale() > 2)) {
            throw new InvalidSpecException("Oil capacity must be between 0.01 and 99.99 liters, two decimals at most");
        }
        String normalizedVersion = VehicleNames.text(version);
        String normalizedEngine = VehicleNames.text(engine);
        requireMaxLength("brand", normalizedBrand, 60);
        requireMaxLength("model", normalizedModel, 100);
        requireMaxLength("version", normalizedVersion, 100);
        requireMaxLength("engine", normalizedEngine, 60);
        return new SpecDraft(normalizedBrand, normalizedModel, normalizedVersion, normalizedEngine,
                fuelType, yearStart, yearEnd, oilCapacityLiters);
    }

    private static void requireMaxLength(String field, String value, int max) {
        if (value != null && value.length() > max) {
            throw new InvalidSpecException("'" + field + "' is longer than " + max + " characters");
        }
    }

    VehicleSpec toEntity() {
        VehicleSpec spec = new VehicleSpec();
        applyTo(spec);
        return spec;
    }

    void applyTo(VehicleSpec spec) {
        spec.setBrand(brand);
        spec.setModel(model);
        spec.setVersion(version);
        spec.setEngine(engine);
        spec.setFuel(fuel);
        spec.setYearStart(yearStart);
        spec.setYearEnd(yearEnd);
        spec.setOilCapacityLiters(oilCapacityLiters);
    }

    String fuelName() {
        return fuel == null ? null : fuel.name();
    }
}
