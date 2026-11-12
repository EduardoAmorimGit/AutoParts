package com.autoparts_maven.autoparts_maven.integration.cep;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class ViaCepLookupProviderTest {

    private MockRestServiceServer server;
    private ViaCepLookupProvider provider;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://viacep.com.br");
        server = MockRestServiceServer.bindTo(builder).build();
        provider = new ViaCepLookupProvider(builder.build());
    }

    @Test
    void mapsAnExistingCep() {
        server.expect(requestTo("https://viacep.com.br/ws/22041001/json/")).andRespond(withSuccess("""
                {"cep":"22041-001","logradouro":"Avenida Atlântica","bairro":"Copacabana",
                 "localidade":"Rio de Janeiro","uf":"RJ"}""", MediaType.APPLICATION_JSON));

        assertThat(provider.lookup("22041001")).contains(
                new CepData("22041001", "Avenida Atlântica", "Copacabana", "Rio de Janeiro", "RJ"));
    }

    @Test
    void unknownOrMalformedCepsAreEmpty() {
        server.expect(requestTo("https://viacep.com.br/ws/99999999/json/"))
                .andRespond(withSuccess("{\"erro\": true}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://viacep.com.br/ws/00000000/json/"))
                .andRespond(withSuccess("{\"erro\": \"true\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://viacep.com.br/ws/11111111/json/")).andRespond(withBadRequest());

        assertThat(provider.lookup("99999999")).isEmpty();
        assertThat(provider.lookup("00000000")).isEmpty();
        assertThat(provider.lookup("11111111")).isEmpty();
        assertThat(provider.lookup("123")).isEmpty();
    }

    @Test
    void providerErrorsAreUnavailable() {
        server.expect(requestTo("https://viacep.com.br/ws/20040020/json/")).andRespond(withServerError());

        assertThatThrownBy(() -> provider.lookup("20040020")).isInstanceOf(CepLookupUnavailableException.class);
    }
}
