package com.autoparts_maven.autoparts_maven.compatibility;

import static org.assertj.core.api.Assertions.assertThat;

import com.autoparts_maven.autoparts_maven.compatibility.SpecMatcher.VehicleFacts;
import java.util.List;
import org.junit.jupiter.api.Test;

class SpecMatcherTest {

    private static final VehicleFacts GOL_2019 =
            VehicleFacts.of("VW", "VW/GOL 1.0", null, 2018, 2019, 999, "Alcool / Gasolina");

    @Test
    void matchesBrandAliasModelYearEngineAndFuel() {
        VehicleSpec gol = spec("VOLKSWAGEN", "GOL", null, "1.0 8V", FuelType.FLEX, 2017, 2022);

        assertThat(SpecMatcher.match(GOL_2019, List.of(gol))).contains(gol);
    }

    @Test
    void usesModelYearWhenPresent() {
        VehicleSpec until2018 = spec("VOLKSWAGEN", "GOL", null, null, null, 2015, 2018);

        assertThat(SpecMatcher.match(GOL_2019, List.of(until2018))).isEmpty();
    }

    @Test
    void rejectsDifferentDisplacementOrFuel() {
        VehicleSpec gol16 = spec("VOLKSWAGEN", "GOL", null, "1.6", null, 2017, 2022);
        VehicleSpec golDiesel = spec("VOLKSWAGEN", "GOL", null, null, FuelType.DIESEL, 2017, 2022);

        assertThat(SpecMatcher.match(GOL_2019, List.of(gol16, golDiesel))).isEmpty();
    }

    @Test
    void prefersTheMoreSpecificModelName() {
        VehicleFacts golG5 = VehicleFacts.of("VOLKSWAGEN", "GOL G5 1.0", null, 2012, 2012, 999, "FLEX");
        VehicleSpec gol = spec("VOLKSWAGEN", "GOL", null, null, null, 2008, 2014);
        VehicleSpec g5 = spec("VOLKSWAGEN", "GOL G5", null, null, null, 2008, 2014);

        assertThat(SpecMatcher.match(golG5, List.of(gol, g5))).contains(g5);
    }

    @Test
    void leavesAmbiguousCasesPending() {
        VehicleFacts noValves = VehicleFacts.of("VOLKSWAGEN", "GOL 1.0", null, 2019, 2019, 999, "FLEX");
        VehicleSpec eightValves = spec("VOLKSWAGEN", "GOL", null, "1.0 8V", null, 2017, 2022);
        VehicleSpec twelveValves = spec("VOLKSWAGEN", "GOL", null, "1.0 12V", null, 2017, 2022);

        assertThat(SpecMatcher.match(noValves, List.of(eightValves, twelveValves))).isEmpty();
    }

    @Test
    void requiresStatedVersion() {
        VehicleSpec highline = spec("VOLKSWAGEN", "GOL", "HIGHLINE", null, null, 2017, 2022);

        assertThat(SpecMatcher.match(GOL_2019, List.of(highline))).isEmpty();
    }

    @Test
    void doesNotMatchAnotherModelThatSharesAPrefixWord() {
        VehicleFacts golf = VehicleFacts.of("VOLKSWAGEN", "GOLF 1.4 TSI", null, 2019, 2019, 1395, "FLEX");
        VehicleSpec gol = spec("VOLKSWAGEN", "GOL", null, null, null, 2017, 2022);

        assertThat(SpecMatcher.match(golf, List.of(gol))).isEmpty();
    }

    @Test
    void signatureIsStableAcrossFormatting() {
        VehicleFacts a = VehicleFacts.of("VW", "VW/GOL 1.0", null, 2018, 2019, 999, "Alcool / Gasolina");
        VehicleFacts b = VehicleFacts.of("volkswagen", "gol 1,0", null, 2018, 2019, 999, "FLEX");

        assertThat(a.signature()).isEqualTo(b.signature());
    }

    private static VehicleSpec spec(String brand, String model, String version, String engine, FuelType fuel,
            int from, int to) {
        VehicleSpec spec = new VehicleSpec();
        spec.setBrand(brand);
        spec.setModel(model);
        spec.setVersion(version);
        spec.setEngine(engine);
        spec.setFuel(fuel);
        spec.setYearStart(from);
        spec.setYearEnd(to);
        return spec;
    }
}
