package com.autoparts_maven.autoparts_maven.compatibility.dto;

import com.autoparts_maven.autoparts_maven.common.PageResponse;
import com.autoparts_maven.autoparts_maven.compatibility.FitmentStatus;
import com.autoparts_maven.autoparts_maven.compatibility.ProductFitment;
import com.autoparts_maven.autoparts_maven.compatibility.VehicleSpec;
import com.autoparts_maven.autoparts_maven.product.dto.ProductResponse;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

public final class CompatibilityDtos {

    private CompatibilityDtos() {
    }

    /** Customer view: RESOLVED with the spec and its validated products, or PENDING (no spec yet). */
    public record CompatibleProductsResponse(String status, VehicleSpecResponse spec,
            PageResponse<ProductResponse> products, List<KitResponse> kits) {
    }

    /** Ready-made combination of fitted products (e.g. oil change: N units of oil + oil filter). */
    public record KitResponse(String name, BigDecimal oilCapacityLiters, List<KitItem> items, BigDecimal total) {
    }

    public record KitItem(ProductResponse product, int quantity) {
    }

    public record VehicleSpecResponse(Long id, String brand, String model, String version, String engine,
            String fuel, Integer yearStart, Integer yearEnd, BigDecimal oilCapacityLiters) {

        public static VehicleSpecResponse from(VehicleSpec spec) {
            return new VehicleSpecResponse(spec.getId(), spec.getBrand(), spec.getModel(), spec.getVersion(),
                    spec.getEngine(), spec.getFuel() == null ? null : spec.getFuel().name(),
                    spec.getYearStart(), spec.getYearEnd(), spec.getOilCapacityLiters());
        }
    }

    public record VehicleSpecRequest(
            @NotBlank @Size(max = 60) String brand,
            @NotBlank @Size(max = 100) String model,
            @Size(max = 100) String version,
            @Size(max = 60) String engine,
            @Size(max = 20) String fuel,
            @NotNull @Min(1950) @Max(2100) Integer yearStart,
            @NotNull @Min(1950) @Max(2100) Integer yearEnd,
            @DecimalMin(value = "0.1") @Digits(integer = 2, fraction = 2) BigDecimal oilCapacityLiters) {
    }

    public record FitmentResponse(Long id, Long productId, String productSku, String productName,
            VehicleSpecResponse spec, String status, String source, String notes, Long reviewedBy,
            Instant reviewedAt) {

        public static FitmentResponse from(ProductFitment fitment) {
            return new FitmentResponse(fitment.getId(), fitment.getProduct().getId(), fitment.getProduct().getSku(),
                    fitment.getProduct().getName(), VehicleSpecResponse.from(fitment.getSpec()),
                    fitment.getStatus().name(), fitment.getSource(), fitment.getNotes(), fitment.getReviewedBy(),
                    fitment.getReviewedAt());
        }
    }

    /** Manual fitment by an administrator: validated immediately, source still required. */
    public record FitmentRequest(
            @NotNull Long productId,
            @NotNull Long specId,
            @NotBlank @Size(max = 300) String source,
            @Size(max = 500) String notes) {
    }

    public record ReviewRequest(@NotEmpty @Size(max = 1000) List<Long> ids, @NotNull FitmentStatus decision) {
    }

    public record ReviewResult(int updated) {
    }

    public record AssignSpecRequest(@NotNull Long specId) {
    }

    public record RematchResult(int resolvedVehicles) {
    }

    /** Vehicle waiting for a spec, with the provider data needed to create or pick one. */
    public record PendingVehicleResponse(Long id, String brand, String model, String version,
            Integer manufactureYear, Integer modelYear, Integer engineCc, String fuel, LocalDateTime lookedUpAt) {
    }

    public record ImportReport(boolean dryRun, int totalRows, int fitmentsCreated, int fitmentsAlreadyPresent,
            int specsCreated, List<RowMessage> errors, List<RowMessage> warnings) {
    }

    public record RowMessage(int line, String message) {
    }
}
