package com.autoparts_maven.autoparts_maven.compatibility;

import com.autoparts_maven.autoparts_maven.compatibility.dto.CompatibilityDtos.KitItem;
import com.autoparts_maven.autoparts_maven.compatibility.dto.CompatibilityDtos.KitResponse;
import com.autoparts_maven.autoparts_maven.product.Product;
import com.autoparts_maven.autoparts_maven.product.ProductType;
import com.autoparts_maven.autoparts_maven.product.dto.ProductResponse;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Builds the "oil change" suggestion for a spec: enough units of a fitted engine oil to cover the
 * spec's oil capacity plus one fitted oil filter. It only combines products that already have a
 * validated fitment for the spec, so it never suggests an unconfirmed item. Among valid options
 * it picks the lowest total price; items without enough stock are not considered.
 */
final class OilChangeKitBuilder {

    static final String NAME = "Troca de oleo";

    private OilChangeKitBuilder() {
    }

    static Optional<KitResponse> build(VehicleSpec spec, List<Product> fittedProducts) {
        BigDecimal capacity = spec.getOilCapacityLiters();
        if (capacity == null || capacity.signum() <= 0) {
            return Optional.empty();
        }
        Optional<Product> filter = fittedProducts.stream()
                .filter(p -> p.getType() == ProductType.OIL_FILTER && p.getStockQuantity() > 0)
                .min(Comparator.comparing(Product::getPrice));
        Optional<OilChoice> oil = fittedProducts.stream()
                .filter(p -> p.getType() == ProductType.ENGINE_OIL && p.getPackageVolumeLiters() != null
                        && p.getPackageVolumeLiters().signum() > 0)
                .map(p -> new OilChoice(p, unitsFor(capacity, p.getPackageVolumeLiters())))
                .filter(choice -> choice.product().getStockQuantity() >= choice.units())
                .min(Comparator.comparing(OilChoice::total));
        if (filter.isEmpty() || oil.isEmpty()) {
            return Optional.empty();
        }
        BigDecimal total = oil.get().total().add(filter.get().getPrice());
        return Optional.of(new KitResponse(NAME, capacity, List.of(
                new KitItem(ProductResponse.from(oil.get().product()), oil.get().units()),
                new KitItem(ProductResponse.from(filter.get()), 1)), total));
    }

    /** 3.5 L with 1 L bottles = 4 units; never less oil than the engine takes. */
    static int unitsFor(BigDecimal capacity, BigDecimal packageVolume) {
        return capacity.divide(packageVolume, 0, RoundingMode.CEILING).intValueExact();
    }

    private record OilChoice(Product product, int units) {

        BigDecimal total() {
            return product.getPrice().multiply(BigDecimal.valueOf(units));
        }
    }
}
