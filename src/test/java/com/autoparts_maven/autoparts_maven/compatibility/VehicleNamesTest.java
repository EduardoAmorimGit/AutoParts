package com.autoparts_maven.autoparts_maven.compatibility;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class VehicleNamesTest {

    @Test
    void normalizesCaseAccentsDecimalCommaAndSeparators() {
        assertThat(VehicleNames.text("  Citroën  C3 1,6/16v ")).isEqualTo("CITROEN C3 1.6 16V");
        assertThat(VehicleNames.text("   ")).isNull();
    }

    @Test
    void mapsBrandAliasesAndImportedPrefix() {
        assertThat(VehicleNames.brand("VW")).isEqualTo("VOLKSWAGEN");
        assertThat(VehicleNames.brand("GM - Chevrolet")).isEqualTo("CHEVROLET");
        assertThat(VehicleNames.brand("I/Toyota")).isEqualTo("TOYOTA");
    }

    @Test
    void stripsBrandFromModel() {
        assertThat(VehicleNames.model("VW/GOL 1.0", "VOLKSWAGEN")).isEqualTo("GOL 1.0");
        assertThat(VehicleNames.model("CHEV/ONIX 10MT", "CHEVROLET")).isEqualTo("ONIX 10MT");
        assertThat(VehicleNames.model("I/TOYOTA COROLLA", "TOYOTA")).isEqualTo("COROLLA");
    }

    @Test
    void readsDisplacementAndValves() {
        assertThat(VehicleNames.displacementCc("GOL 1.6 16V")).contains(1600);
        assertThat(VehicleNames.valves("GOL 1.6 16V")).contains("16V");
        assertThat(VehicleNames.displacementCc("GOL")).isEmpty();
    }

    @Test
    void parsesFuelAsWrittenByProviderAndCatalogs() {
        assertThat(FuelType.parse("Alcool / Gasolina")).contains(FuelType.FLEX);
        assertThat(FuelType.parse("FLEX")).contains(FuelType.FLEX);
        assertThat(FuelType.parse("Gasolina")).contains(FuelType.GASOLINA);
        assertThat(FuelType.parse("Etanol")).contains(FuelType.ETANOL);
        assertThat(FuelType.parse("Diesel")).contains(FuelType.DIESEL);
        assertThat(FuelType.parse("Querosene")).isEmpty();
    }
}
