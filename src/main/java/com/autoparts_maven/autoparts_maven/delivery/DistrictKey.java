package com.autoparts_maven.autoparts_maven.delivery;

import java.text.Normalizer;
import java.util.Locale;

/** Comparison key of a district (bairro) name: no accents, upper-case, single spaces. */
public final class DistrictKey {

    private DistrictKey() {
    }

    public static String of(String district) {
        if (district == null) {
            return "";
        }
        return Normalizer.normalize(district, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .trim().replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
    }
}
