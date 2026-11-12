package com.autoparts_maven.autoparts_maven.integration.cep;

import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;

/**
 * CEP lookups on ViaCEP (free, no key): {@code GET /ws/{cep}/json/}. An unknown CEP answers
 * 200 with {@code "erro": true}; a malformed one answers 400.
 */
public class ViaCepLookupProvider implements CepLookupProvider {

    private static final Logger log = LoggerFactory.getLogger(ViaCepLookupProvider.class);

    private final RestClient restClient;

    public ViaCepLookupProvider(RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public Optional<CepData> lookup(String cep) {
        if (cep == null || !cep.matches("\\d{8}")) {
            return Optional.empty();
        }
        JsonNode response;
        try {
            response = restClient.get().uri("/ws/{cep}/json/", cep).retrieve().body(JsonNode.class);
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() == 400) {
                return Optional.empty();
            }
            log.warn("CEP provider returned status {}", exception.getStatusCode().value());
            throw new CepLookupUnavailableException("CEP provider returned an error", exception);
        } catch (RestClientException exception) {
            log.warn("CEP provider is unreachable: {}", exception.getClass().getSimpleName());
            throw new CepLookupUnavailableException("CEP provider is unreachable", exception);
        }
        if (response == null || response.path("erro").asBoolean(false)
                || "true".equals(response.path("erro").asString(""))) {
            return Optional.empty();
        }
        String city = text(response, "localidade");
        String state = text(response, "uf");
        if (city.isEmpty() || state.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new CepData(cep, text(response, "logradouro"), text(response, "bairro"), city, state));
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? "" : value.asString("").trim();
    }
}
