package com.autoparts_maven.autoparts_maven.compatibility;

import static org.assertj.core.api.Assertions.assertThat;

import com.autoparts_maven.autoparts_maven.compatibility.dto.CompatibilityDtos.KitResponse;
import com.autoparts_maven.autoparts_maven.product.Product;
import com.autoparts_maven.autoparts_maven.product.ProductType;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class OilChangeKitBuilderTest {

    @Test
    void roundsOilUnitsUp() {
        assertThat(OilChangeKitBuilder.unitsFor(new BigDecimal("3.5"), BigDecimal.ONE)).isEqualTo(4);
        assertThat(OilChangeKitBuilder.unitsFor(new BigDecimal("4.0"), BigDecimal.ONE)).isEqualTo(4);
        assertThat(OilChangeKitBuilder.unitsFor(new BigDecimal("4.2"), new BigDecimal("4"))).isEqualTo(2);
    }

    @Test
    void picksCheapestCompleteCombinationWithEnoughStock() {
        Product cheapOil = product(1L, ProductType.ENGINE_OIL, "30.00", 10, "1");
        Product outOfStockOil = product(2L, ProductType.ENGINE_OIL, "10.00", 2, "1");
        Product filter = product(3L, ProductType.OIL_FILTER, "25.00", 1, null);
        Product pricierFilter = product(4L, ProductType.OIL_FILTER, "40.00", 9, null);

        KitResponse kit = OilChangeKitBuilder.build(spec("3.5"),
                List.of(cheapOil, outOfStockOil, filter, pricierFilter)).orElseThrow();

        assertThat(kit.items()).extracting(item -> item.product().id(), item -> item.quantity())
                .containsExactly(org.assertj.core.groups.Tuple.tuple(1L, 4), org.assertj.core.groups.Tuple.tuple(3L, 1));
        assertThat(kit.total()).isEqualByComparingTo("145.00");
    }

    @Test
    void noKitWithoutCapacityOilOrFilter() {
        Product oil = product(1L, ProductType.ENGINE_OIL, "30.00", 10, "1");
        Product filter = product(2L, ProductType.OIL_FILTER, "25.00", 5, null);

        assertThat(OilChangeKitBuilder.build(spec(null), List.of(oil, filter))).isEmpty();
        assertThat(OilChangeKitBuilder.build(spec("3.5"), List.of(filter))).isEmpty();
        assertThat(OilChangeKitBuilder.build(spec("3.5"), List.of(oil))).isEmpty();
    }

    private static VehicleSpec spec(String oilLiters) {
        VehicleSpec spec = new VehicleSpec();
        spec.setOilCapacityLiters(oilLiters == null ? null : new BigDecimal(oilLiters));
        return spec;
    }

    private static Product product(Long id, ProductType type, String price, int stock, String volume) {
        Product product = new Product();
        product.setId(id);
        product.setSku("SKU-" + id);
        product.setName("P" + id);
        product.setType(type);
        product.setPrice(new BigDecimal(price));
        product.setStockQuantity(stock);
        product.setActive(true);
        product.setPackageVolumeLiters(volume == null ? null : new BigDecimal(volume));
        return product;
    }
}
