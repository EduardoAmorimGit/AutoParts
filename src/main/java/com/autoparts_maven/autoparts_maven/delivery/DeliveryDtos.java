package com.autoparts_maven.autoparts_maven.delivery;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

public final class DeliveryDtos {

    private DeliveryDtos() {
    }

    /**
     * Creates or replaces a region; {@code districts} is the full list of bairros it serves. A null
     * {@code fee} means "not set yet": the region then serves nobody.
     */
    public record RegionRequest(
            @NotBlank @Size(max = 80) String name,
            @DecimalMin("0.00") @Digits(integer = 8, fraction = 2) BigDecimal fee,
            @NotNull Boolean active,
            @NotNull @Size(min = 1, max = 300) List<@NotBlank @Size(max = 100) String> districts) {
    }

    public record RegionResponse(Long id, String name, BigDecimal fee, boolean active, List<String> districts,
            Long updatedBy, Instant updatedAt) {

        static RegionResponse from(DeliveryRegion region) {
            return new RegionResponse(region.getId(), region.getName(), region.getFee(), region.isActive(),
                    region.getDistricts().stream()
                            .sorted(Comparator.comparing(RegionDistrict::getDistrictKey))
                            .map(RegionDistrict::getName).toList(),
                    region.getUpdatedBy(), region.getUpdatedAt());
        }
    }

    /** A district customers saved addresses in that no active region serves, with how many addresses. */
    public record UncoveredDistrict(String district, long addresses) {
    }

    /** The fee that applies to a district and the region it comes from. */
    public record DeliveryQuote(Long regionId, String region, BigDecimal fee) {
    }
}
