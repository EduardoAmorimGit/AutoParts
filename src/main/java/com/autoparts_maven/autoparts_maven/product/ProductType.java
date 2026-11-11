package com.autoparts_maven.autoparts_maven.product;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public enum ProductType {
    ENGINE_OIL,
    OIL_FILTER,
    AIR_FILTER,
    FUEL_FILTER,
    CABIN_FILTER,
    OTHER;

    // Portuguese names used in spreadsheets. A bare "OLEO" is deliberately absent: it could mean
    // the engine oil or the oil filter, so such rows are rejected instead of guessed.
    private static final Map<String, ProductType> ALIASES = Map.ofEntries(
            Map.entry("OLEO DO MOTOR", ENGINE_OIL),
            Map.entry("OLEO MOTOR", ENGINE_OIL),
            Map.entry("OLEO LUBRIFICANTE", ENGINE_OIL),
            Map.entry("LUBRIFICANTE", ENGINE_OIL),
            Map.entry("FILTRO DE OLEO", OIL_FILTER),
            Map.entry("FILTRO OLEO", OIL_FILTER),
            Map.entry("FILTRO DE AR", AIR_FILTER),
            Map.entry("FILTRO AR", AIR_FILTER),
            Map.entry("FILTRO DE COMBUSTIVEL", FUEL_FILTER),
            Map.entry("FILTRO COMBUSTIVEL", FUEL_FILTER),
            Map.entry("FILTRO DE CABINE", CABIN_FILTER),
            Map.entry("FILTRO CABINE", CABIN_FILTER),
            Map.entry("FILTRO DE AR CONDICIONADO", CABIN_FILTER),
            Map.entry("OUTRO", OTHER),
            Map.entry("OUTROS", OTHER));

    /** Accepts enum names ("OIL_FILTER") or Portuguese names ("Filtro de óleo"). */
    public static Optional<ProductType> parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String text = Normalizer.normalize(raw, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toUpperCase(Locale.ROOT).trim();
        for (ProductType type : values()) {
            if (type.name().equals(text.replace(' ', '_'))) {
                return Optional.of(type);
            }
        }
        return Optional.ofNullable(ALIASES.get(text.replaceAll("[^A-Z ]", " ").replaceAll(" +", " ").trim()));
    }
}
