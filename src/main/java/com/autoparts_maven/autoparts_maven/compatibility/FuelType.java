package com.autoparts_maven.autoparts_maven.compatibility;

import java.util.Optional;

public enum FuelType {
    FLEX,
    GASOLINA,
    ETANOL,
    DIESEL,
    GNV,
    ELETRICO,
    HIBRIDO;

    /**
     * Reads the fuel as provider and catalogs write it ("Alcool / Gasolina", "Flex", "Gasolina").
     * Returns empty for blank or unrecognized text so callers decide whether that is an error.
     */
    public static Optional<FuelType> parse(String raw) {
        String text = VehicleNames.text(raw);
        if (text == null) {
            return Optional.empty();
        }
        boolean alcohol = text.contains("ALCOOL") || text.contains("ETANOL");
        boolean gasoline = text.contains("GASOLINA");
        if (text.contains("FLEX") || (alcohol && gasoline)) {
            return Optional.of(FLEX);
        }
        if (text.contains("HIBRID")) {
            return Optional.of(HIBRIDO);
        }
        if (text.contains("ELETRIC")) {
            return Optional.of(ELETRICO);
        }
        if (text.contains("DIESEL")) {
            return Optional.of(DIESEL);
        }
        if (text.contains("GNV") || text.contains("GAS NATURAL")) {
            return Optional.of(GNV);
        }
        if (gasoline) {
            return Optional.of(GASOLINA);
        }
        if (alcohol) {
            return Optional.of(ETANOL);
        }
        return Optional.empty();
    }
}
