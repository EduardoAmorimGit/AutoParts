package com.autoparts_maven.autoparts_maven.integration.vehicle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class SimplesApiVehicleLookupProviderTest {

    private SimplesApiProperties properties;
    private RestClient.Builder builder;
    private MockRestServiceServer server;
    private SimplesApiVehicleLookupProvider provider;

    @BeforeEach
    void setUp() {
        properties = new SimplesApiProperties();
        properties.setBaseUrl("https://api.simplesapi.com.br");
        properties.setPlatePath("/v1/vehicle/plate");
        properties.setApiKey("test-key");

        builder = RestClient.builder()
                .baseUrl(properties.getBaseUrl())
                .defaultHeader("Authorization", "Bearer " + properties.getApiKey());
        server = MockRestServiceServer.bindTo(builder).build();
        provider = new SimplesApiVehicleLookupProvider(builder.build(), properties);
    }

    @Test
    void mapsCompleteResponse() {
        server.expect(requestTo("https://api.simplesapi.com.br/v1/vehicle/plate"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer test-key"))
                .andExpect(jsonPath("$.plate").value("ABC1D23"))
                .andRespond(withSuccess("""
                        {
                          "plate": "ABC1D23",
                          "vehicle_info": {
                            "brand": "VOLKSWAGEN",
                            "model": "GOL",
                            "version": "1.0 MPI TOTALFLEX",
                            "year": 2020,
                            "model_year": 2021,
                            "color": "BRANCO",
                            "fuel": "FLEX",
                            "vehicle_type": "AUTOMOVEL",
                            "species": "PASSAGEIRO",
                            "passenger_capacity": 5,
                            "engine_cc": 1000,
                            "origin": "NACIONAL"
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        Optional<VehicleData> result = provider.lookupByPlate("ABC1D23");

        assertThat(result).isPresent();
        VehicleData data = result.get();
        assertThat(data.plate()).isEqualTo("ABC1D23");
        assertThat(data.brand()).isEqualTo("VOLKSWAGEN");
        assertThat(data.model()).isEqualTo("GOL");
        assertThat(data.version()).isEqualTo("1.0 MPI TOTALFLEX");
        assertThat(data.manufactureYear()).isEqualTo(2020);
        assertThat(data.modelYear()).isEqualTo(2021);
        assertThat(data.engineCc()).isEqualTo(1000);
        assertThat(data.passengerCapacity()).isEqualTo(5);
        server.verify();
    }

    @Test
    void keepsNullableFieldsAsNullWithoutInventingData() {
        server.expect(requestTo("https://api.simplesapi.com.br/v1/vehicle/plate"))
                .andRespond(withSuccess("""
                        {
                          "plate": "KYM7I30",
                          "vehicle_info": {
                            "brand": "FORD",
                            "model": "FIESTA 16SE",
                            "version": null,
                            "year": 2017,
                            "model_year": 2018,
                            "color": "BRANCA",
                            "fuel": "Alcool / Gasolina",
                            "engine_cc": 1597
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        VehicleData data = provider.lookupByPlate("KYM7I30").orElseThrow();

        assertThat(data.version()).isNull();
        assertThat(data.manufactureYear()).isEqualTo(2017);
        assertThat(data.modelYear()).isEqualTo(2018);
        assertThat(data.engineCc()).isEqualTo(1597);
        server.verify();
    }

    @Test
    void returnsEmptyWhenVehicleInfoIsMissing() {
        server.expect(requestTo("https://api.simplesapi.com.br/v1/vehicle/plate"))
                .andRespond(withSuccess("""
                        {"plate": "ZZZ0000", "vehicle_info": null}
                        """, MediaType.APPLICATION_JSON));

        assertThat(provider.lookupByPlate("ZZZ0000")).isEmpty();
        server.verify();
    }

    @Test
    void ignoresMissingFieldsWithoutFailing() {
        server.expect(requestTo("https://api.simplesapi.com.br/v1/vehicle/plate"))
                .andRespond(withSuccess("""
                        {"vehicle_info": {"brand": "FORD", "model": "KA", "year": 2019}}
                        """, MediaType.APPLICATION_JSON));

        VehicleData data = provider.lookupByPlate("ABC1234").orElseThrow();

        assertThat(data.plate()).isEqualTo("ABC1234");
        assertThat(data.engineCc()).isNull();
        server.verify();
    }

    @Test
    void mapsUnauthorizedToAuthenticationException() {
        server.expect(requestTo("https://api.simplesapi.com.br/v1/vehicle/plate"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(() -> provider.lookupByPlate("ABC1D23"))
                .isInstanceOf(VehicleLookupAuthenticationException.class);
        server.verify();
    }

    @Test
    void mapsTooManyRequestsToRateLimitedException() {
        server.expect(requestTo("https://api.simplesapi.com.br/v1/vehicle/plate"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        assertThatThrownBy(() -> provider.lookupByPlate("ABC1D23"))
                .isInstanceOf(VehicleLookupRateLimitedException.class);
        server.verify();
    }

    @Test
    void mapsUnprocessableEntityToProviderException() {
        server.expect(requestTo("https://api.simplesapi.com.br/v1/vehicle/plate"))
                .andRespond(withStatus(HttpStatus.UNPROCESSABLE_ENTITY));

        assertThatThrownBy(() -> provider.lookupByPlate("ABC1D23"))
                .isInstanceOf(VehicleLookupProviderException.class);
        server.verify();
    }

    @Test
    void mapsServiceUnavailableToUnavailableException() {
        server.expect(requestTo("https://api.simplesapi.com.br/v1/vehicle/plate"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThatThrownBy(() -> provider.lookupByPlate("ABC1D23"))
                .isInstanceOf(VehicleLookupUnavailableException.class);
        server.verify();
    }

    @Test
    void mapsUnexpectedStatusToProviderException() {
        server.expect(requestTo("https://api.simplesapi.com.br/v1/vehicle/plate"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThatThrownBy(() -> provider.lookupByPlate("ABC1D23"))
                .isInstanceOf(VehicleLookupProviderException.class);
        server.verify();
    }

    @Test
    void mapsMalformedSuccessBodyToProviderExceptionWithoutLeakingPayload() {
        server.expect(requestTo("https://api.simplesapi.com.br/v1/vehicle/plate"))
                .andRespond(withSuccess("not-a-json-body-secret-marker", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> provider.lookupByPlate("ABC1D23"))
                .isInstanceOf(VehicleLookupProviderException.class)
                .hasMessageNotContaining("not-a-json-body-secret-marker")
                .hasMessageNotContaining("test-key");
        server.verify();
    }
}
