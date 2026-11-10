package com.autoparts_maven.autoparts_maven.integration.vehicle;

import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Wires the Simples API provider only when an API key is configured. Without a key no client or
 * provider bean exists and plate lookups answer 503 (see {@code PlateLookup}).
 */
@Configuration
@ConditionalOnExpression("'${simples-api.api-key:}'.trim().length() > 0")
@EnableConfigurationProperties(SimplesApiProperties.class)
public class SimplesApiClientConfig {

    @Bean
    RestClient simplesApiRestClient(SimplesApiProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(properties.getConnectTimeoutSeconds()))
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(properties.getReadTimeoutSeconds()));

        return RestClient.builder()
                .baseUrl(properties.getBaseUrl())
                .requestFactory(requestFactory)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getApiKey())
                .build();
    }

    @Bean
    VehicleLookupProvider simplesApiVehicleLookupProvider(
            RestClient simplesApiRestClient, SimplesApiProperties properties) {
        return new SimplesApiVehicleLookupProvider(simplesApiRestClient, properties);
    }
}
