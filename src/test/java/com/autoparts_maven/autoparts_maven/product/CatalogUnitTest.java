package com.autoparts_maven.autoparts_maven.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class CatalogUnitTest {

    @Test
    void parsesProductTypesInEnglishOrPortuguese() {
        assertThat(ProductType.parse("OIL_FILTER")).contains(ProductType.OIL_FILTER);
        assertThat(ProductType.parse("Filtro de óleo")).contains(ProductType.OIL_FILTER);
        assertThat(ProductType.parse("filtro de ar")).contains(ProductType.AIR_FILTER);
        assertThat(ProductType.parse("Óleo do motor")).contains(ProductType.ENGINE_OIL);
        assertThat(ProductType.parse("Filtro de cabine")).contains(ProductType.CABIN_FILTER);
    }

    @Test
    void rejectsAmbiguousOrUnknownTypes() {
        assertThat(ProductType.parse("Oleo")).isEmpty();
        assertThat(ProductType.parse("Pneu")).isEmpty();
        assertThat(ProductType.parse(" ")).isEmpty();
    }

    @Test
    void buildsCategorySlugs() {
        assertThat(CategoryService.slugOf("Filtros de Óleo")).isEqualTo("filtros-de-oleo");
        assertThat(CategoryService.slugOf("  Óleos / Lubrificantes ")).isEqualTo("oleos-lubrificantes");
        assertThatThrownBy(() -> CategoryService.slugOf("!!!")).isInstanceOf(InvalidCategoryNameException.class);
    }

    @Test
    void normalizesViscosity() {
        assertThat(ProductService.normalizeViscosity("5w-30")).isEqualTo("5W30");
        assertThat(ProductService.normalizeViscosity(" 10W 40 ")).isEqualTo("10W40");
        assertThat(ProductService.normalizeViscosity(" ")).isNull();
    }
}
