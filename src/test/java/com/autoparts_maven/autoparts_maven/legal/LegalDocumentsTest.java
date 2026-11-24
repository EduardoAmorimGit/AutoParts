package com.autoparts_maven.autoparts_maven.legal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LegalDocumentsTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void documentsArePublicAndCarryTheTermsVersion() throws Exception {
        mockMvc.perform(get("/api/legal/documents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value("test-terms"))
                .andExpect(jsonPath("$.documents.length()").value(3))
                .andExpect(jsonPath("$.documents[0].slug").value("termos-de-uso"));
        mockMvc.perform(get("/api/legal/documents/politica-de-trocas-devolucoes-e-reembolso"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Política de Trocas, Devoluções e Reembolso"));
        mockMvc.perform(get("/api/legal/documents/nao-existe")).andExpect(status().isNotFound());
    }

    @Test
    void placeholdersAreFilledWithTheSellerIdentification() {
        StoreInfo store = new StoreInfo("AutoParts", "AutoParts Comercio LTDA", "12.345.678/0001-90",
                "Rua X, 10 - Centro, Rio de Janeiro/RJ", "sac@autoparts.test", "(21) 99999-0000", "https://autoparts.test");
        LegalDocuments documents = new LegalDocuments(store, "2026-11-24");

        String policy = documents.get("politica-de-trocas-devolucoes-e-reembolso").orElseThrow().markdown();
        assertThat(policy).contains("AutoParts Comercio LTDA, CNPJ 12.345.678/0001-90", "Versão 2026-11-24",
                "7 dias corridos", "sac@autoparts.test").doesNotContain("{{");
        assertThat(store.isComplete()).isTrue();
    }

    @Test
    void missingSellerDataIsMarkedAndBlocksProduction() {
        StoreInfo incomplete = new StoreInfo("AutoParts", "", "", "", "", "", "");
        assertThat(incomplete.cnpj()).isEqualTo("[A PREENCHER]");
        assertThat(incomplete.isComplete()).isFalse();

        org.springframework.mock.env.MockEnvironment prod = new org.springframework.mock.env.MockEnvironment();
        prod.setActiveProfiles("prod");
        assertThatThrownBy(() -> new StoreInfoProductionCheck(incomplete, prod).run(null))
                .isInstanceOf(IllegalStateException.class);
    }
}
