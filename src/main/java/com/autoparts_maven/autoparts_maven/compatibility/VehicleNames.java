package com.autoparts_maven.autoparts_maven.compatibility;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Normalizes vehicle names so the provider ("VW/GOL 1.0"), catalogs ("Volkswagen Gol 1,0 8V") and
 * specs compare equal: uppercase, no accents, decimal comma as dot, single spaces.
 */
public final class VehicleNames {

    // Short or alternative names that providers and catalogs use for the same brand.
    private static final Map<String, String> BRAND_ALIASES = Map.ofEntries(
            Map.entry("VW", "VOLKSWAGEN"),
            Map.entry("VOLKS", "VOLKSWAGEN"),
            Map.entry("GM", "CHEVROLET"),
            Map.entry("GM CHEVROLET", "CHEVROLET"),
            Map.entry("CHEV", "CHEVROLET"),
            Map.entry("MB", "MERCEDES BENZ"),
            Map.entry("M BENZ", "MERCEDES BENZ"),
            Map.entry("MERCEDES", "MERCEDES BENZ"),
            Map.entry("CAOA CHERY", "CHERY"),
            Map.entry("LR", "LAND ROVER"));

    private static final Pattern DECIMAL_COMMA = Pattern.compile("(\\d),(\\d)");
    private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^A-Z0-9.]+");
    private static final Pattern DISPLACEMENT_LITERS = Pattern.compile("\\b(\\d)\\.(\\d)\\b");
    private static final Pattern VALVES = Pattern.compile("\\b(8|12|16|20|24|32)V\\b");

    private VehicleNames() {
    }

    /** Uppercase, accent-free, single-spaced text; {@code null} when blank. */
    public static String text(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String text = Normalizer.normalize(raw, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        text = DECIMAL_COMMA.matcher(text).replaceAll("$1.$2").toUpperCase(Locale.ROOT);
        text = NON_ALPHANUMERIC.matcher(text).replaceAll(" ").trim().replaceAll(" +", " ");
        return text.isEmpty() ? null : text;
    }

    public static String brand(String raw) {
        String text = withoutImportedPrefix(text(raw));
        if (text == null) {
            return null;
        }
        return BRAND_ALIASES.getOrDefault(text, text);
    }

    /** Model text without a leading brand ("VW GOL 1.0" with brand VOLKSWAGEN becomes "GOL 1.0"). */
    public static String model(String raw, String normalizedBrand) {
        String text = withoutImportedPrefix(text(raw));
        if (text == null || normalizedBrand == null) {
            return text;
        }
        for (String prefix : brandPrefixes(normalizedBrand)) {
            if (text.startsWith(prefix + " ")) {
                return text.substring(prefix.length() + 1);
            }
        }
        return text;
    }

    public static List<String> tokens(String normalizedText) {
        return normalizedText == null ? List.of() : Arrays.asList(normalizedText.split(" "));
    }

    public static boolean startsWithTokens(List<String> text, List<String> prefix) {
        return !prefix.isEmpty() && text.size() >= prefix.size() && text.subList(0, prefix.size()).equals(prefix);
    }

    public static boolean containsAllTokens(List<String> text, List<String> required) {
        return text.containsAll(required);
    }

    /** Displacement in cc from text such as "1.0", "1.6 16V" ("1.0" = 1000). */
    public static Optional<Integer> displacementCc(String normalizedText) {
        if (normalizedText == null) {
            return Optional.empty();
        }
        Matcher matcher = DISPLACEMENT_LITERS.matcher(normalizedText);
        if (!matcher.find()) {
            return Optional.empty();
        }
        return Optional.of(Integer.parseInt(matcher.group(1)) * 1000 + Integer.parseInt(matcher.group(2)) * 100);
    }

    /** Valve count token ("8V", "16V") when present. */
    public static Optional<String> valves(String normalizedText) {
        if (normalizedText == null) {
            return Optional.empty();
        }
        Matcher matcher = VALVES.matcher(normalizedText);
        return matcher.find() ? Optional.of(matcher.group()) : Optional.empty();
    }

    // Registry data marks imported vehicles with "I/" ("I/TOYOTA COROLLA").
    private static String withoutImportedPrefix(String text) {
        return text != null && text.startsWith("I ") ? text.substring(2) : text;
    }

    private static List<String> brandPrefixes(String normalizedBrand) {
        List<String> prefixes = new ArrayList<>();
        prefixes.add(normalizedBrand);
        BRAND_ALIASES.forEach((alias, brand) -> {
            if (brand.equals(normalizedBrand)) {
                prefixes.add(alias);
            }
        });
        // Longest first, so "GM CHEVROLET" is stripped before "GM".
        prefixes.sort((a, b) -> b.length() - a.length());
        return prefixes;
    }
}
