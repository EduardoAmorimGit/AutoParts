package com.autoparts_maven.autoparts_maven.vehicle;

import java.util.Locale;
import java.util.regex.Pattern;

public final class PlateNormalizer {

    private static final Pattern MERCOSUL = Pattern.compile("^[A-Z]{3}[0-9][A-Z][0-9]{2}$");
    private static final Pattern LEGACY = Pattern.compile("^[A-Z]{3}[0-9]{4}$");

    private PlateNormalizer() {
    }

    public static String normalize(String plate) {
        if (plate == null) {
            return null;
        }
        return plate.replace("-", "")
                .replace(" ", "")
                .trim()
                .toUpperCase(Locale.ROOT);
    }

    public static boolean isValid(String normalizedPlate) {
        if (normalizedPlate == null) {
            return false;
        }
        return MERCOSUL.matcher(normalizedPlate).matches()
                || LEGACY.matcher(normalizedPlate).matches();
    }
}
