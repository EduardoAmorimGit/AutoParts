package com.autoparts_maven.autoparts_maven.integration.vehicle;

import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;

public class SimplesApiVehicleLookupProvider implements VehicleLookupProvider {

    private static final Logger log = LoggerFactory.getLogger(SimplesApiVehicleLookupProvider.class);

    private final RestClient restClient;
    private final SimplesApiProperties properties;

    public SimplesApiVehicleLookupProvider(RestClient restClient, SimplesApiProperties properties) {
        this.restClient = restClient;
        this.properties = properties;
    }

    @Override
    public Optional<VehicleData> lookupByPlate(String plate) {
        JsonNode response;
        try {
            response = restClient.post()
                    .uri(properties.getPlatePath())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new PlateRequest(plate))
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientResponseException exception) {
            throw translate(exception);
        } catch (ResourceAccessException exception) {
            log.warn("Vehicle lookup provider is unreachable: {}", exception.getClass().getSimpleName());
            throw new VehicleLookupUnavailableException("Vehicle lookup provider is unreachable", exception);
        } catch (RestClientException exception) {
            log.warn("Vehicle lookup provider communication/conversion failure: {}",
                    exception.getClass().getSimpleName());
            throw new VehicleLookupProviderException("Vehicle lookup provider response could not be processed",
                    exception);
        }

        return toVehicleData(plate, response);
    }

    private RuntimeException translate(RestClientResponseException exception) {
        int status = exception.getStatusCode().value();
        if (status == HttpStatus.UNAUTHORIZED.value() || status == HttpStatus.FORBIDDEN.value()) {
            log.warn("Vehicle lookup provider rejected the credentials (status {})", status);
            return new VehicleLookupAuthenticationException("Vehicle lookup provider rejected the API key");
        }
        if (status == HttpStatus.TOO_MANY_REQUESTS.value()) {
            log.warn("Vehicle lookup provider rate limit exceeded (status {})", status);
            return new VehicleLookupRateLimitedException("Vehicle lookup rate limit exceeded");
        }
        if (status == HttpStatus.UNPROCESSABLE_ENTITY.value()) {
            log.warn("Vehicle lookup provider rejected the plate format (status {})", status);
            return new VehicleLookupProviderException("Vehicle lookup provider rejected the plate format");
        }
        if (status == HttpStatus.SERVICE_UNAVAILABLE.value()) {
            log.warn("Vehicle lookup provider is temporarily unavailable (status {})", status);
            return new VehicleLookupUnavailableException("Vehicle lookup provider is temporarily unavailable");
        }
        log.warn("Vehicle lookup provider returned unexpected status {}", status);
        return new VehicleLookupProviderException("Vehicle lookup provider returned status " + status);
    }

    private Optional<VehicleData> toVehicleData(String requestedPlate, JsonNode response) {
        if (response == null) {
            return Optional.empty();
        }
        JsonNode info = response.path("vehicle_info");
        if (info.isMissingNode() || info.isNull()) {
            return Optional.empty();
        }

        String brand = textOrNull(info, "brand");
        String model = textOrNull(info, "model");
        Integer manufactureYear = intOrNull(info, "year");

        if (brand == null || model == null || manufactureYear == null) {
            return Optional.empty();
        }

        String plate = textOrNull(response, "plate");
        if (plate == null) {
            plate = requestedPlate;
        }

        return Optional.of(new VehicleData(
                plate,
                brand,
                model,
                textOrNull(info, "version"),
                manufactureYear,
                intOrNull(info, "model_year"),
                textOrNull(info, "fuel"),
                textOrNull(info, "vehicle_type"),
                textOrNull(info, "species"),
                intOrNull(info, "passenger_capacity"),
                intOrNull(info, "engine_cc"),
                textOrNull(info, "origin")
        ));
    }

    private String textOrNull(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        String text = value.asString();
        return text == null || text.isBlank() ? null : text;
    }

    private Integer intOrNull(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull() || !value.canConvertToInt()) {
            return null;
        }
        return value.asInt();
    }

    private record PlateRequest(String plate) {
    }
}
