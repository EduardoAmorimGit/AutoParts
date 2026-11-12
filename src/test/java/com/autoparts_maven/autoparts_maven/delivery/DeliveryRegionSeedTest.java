package com.autoparts_maven.autoparts_maven.delivery;

import static org.assertj.core.api.Assertions.assertThat;

import com.autoparts_maven.autoparts_maven.delivery.DeliveryDtos.RegionResponse;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class DeliveryRegionSeedTest {

    @Autowired
    private DeliveryRegionService service;

    @Test
    void rioRegionsAreSeededWithoutFeesAndServeNobodyYet() {
        List<RegionResponse> regions = service.list();

        assertThat(regions).extracting(RegionResponse::name).containsExactlyInAnyOrder(
                "Centro", "Zona Sul", "Zona Norte", "Barra e Jacarepaguá", "Zona Oeste");
        assertThat(regions).allSatisfy(region -> {
            assertThat(region.fee()).isNull();
            assertThat(region.active()).isTrue();
        });
        assertThat(regions).filteredOn(region -> region.name().equals("Zona Sul"))
                .singleElement().satisfies(region -> assertThat(region.districts()).contains("Copacabana", "Gávea"));
        assertThat(service.quote("copacabana")).isEmpty();
    }
}
