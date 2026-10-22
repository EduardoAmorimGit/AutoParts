package com.autoparts_maven.autoparts_maven.integration.vehicle;

import java.time.Duration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(SimplesApiProperties.class)
public class SimplesApiClientConfig {

    @Bean
    RestClient simplesApiRestClient(SimplesApiProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(properties.getConnectTimeoutSeconds()));
        requestFactory.setReadTimeout(Duration.ofSeconds(properties.getReadTimeoutSeconds()));

        return RestClient.builder()
                .baseUrl(properties.getBaseUrl())
                .requestFactory(requestFactory)
                .defaultHeader("Authorization", "Bearer " + properties.getApiKey())
                .build();
    }

    @Bean
    @ConditionalOnExpression("'${simples-api.api-key:}'.trim().length() > 0")
    VehicleLookupProvider simplesApiVehicleLookupProvider(
            RestClient simplesApiRestClient, SimplesApiProperties properties) {
        return new SimplesApiVehicleLookupProvider(simplesApiRestClient, properties);
    }
}
