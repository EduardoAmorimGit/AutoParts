package com.autoparts_maven.autoparts_maven.customer;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Where the store delivers. Today: the city of Rio de Janeiro, recognised by its CEP range
 * (20000-000 to 23799-999) plus city and state. Ranges come from {@code delivery.cep-ranges}
 * ("20000000-23799999,24000000-24399999") so the area can grow without code changes.
 */
@Component
public class DeliveryArea {

    private record Range(int from, int to) {
    }

    private final List<Range> ranges;
    private final String city;
    private final String state;

    public DeliveryArea(
            @Value("${delivery.cep-ranges:20000000-23799999}") String cepRanges,
            @Value("${delivery.city:Rio de Janeiro}") String city,
            @Value("${delivery.state:RJ}") String state) {
        this.ranges = new ArrayList<>();
        for (String range : cepRanges.split(",")) {
            String[] bounds = range.trim().split("-");
            if (bounds.length != 2) {
                throw new IllegalArgumentException("Invalid delivery.cep-ranges entry: " + range);
            }
            ranges.add(new Range(Integer.parseInt(bounds[0].trim()), Integer.parseInt(bounds[1].trim())));
        }
        this.city = normalize(city);
        this.state = state.trim().toUpperCase(Locale.ROOT);
    }

    public boolean covers(String cepDigits, String addressCity, String addressState) {
        if (cepDigits == null || !cepDigits.matches("\\d{8}")) {
            return false;
        }
        int cep = Integer.parseInt(cepDigits);
        boolean inRange = ranges.stream().anyMatch(r -> cep >= r.from() && cep <= r.to());
        return inRange && city.equals(normalize(addressCity))
                && state.equals(addressState == null ? null : addressState.trim().toUpperCase(Locale.ROOT));
    }

    public String description() {
        return "Rio de Janeiro - RJ";
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        return Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .trim().replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
    }
}
