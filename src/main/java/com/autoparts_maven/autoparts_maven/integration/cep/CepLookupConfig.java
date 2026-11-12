package com.autoparts_maven.autoparts_maven.integration.cep;

import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class CepLookupConfig {

    @Bean
    CepLookupProvider viaCepLookupProvider(
            @Value("${cep-lookup.base-url:https://viacep.com.br}") String baseUrl,
            @Value("${cep-lookup.connect-timeout:2s}") Duration connectTimeout,
            @Value("${cep-lookup.read-timeout:4s}") Duration readTimeout) {
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(connectTimeout).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(readTimeout);
        return new ViaCepLookupProvider(RestClient.builder().baseUrl(baseUrl).requestFactory(requestFactory).build());
    }
}
