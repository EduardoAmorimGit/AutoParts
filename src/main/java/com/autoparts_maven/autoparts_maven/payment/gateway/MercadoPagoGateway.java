package com.autoparts_maven.autoparts_maven.payment.gateway;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;

/**
 * Mercado Pago Checkout API ({@code /v1/payments}) over plain REST. Logs carry only the HTTP
 * status and payment ids, never card data, tokens, payer data or credentials.
 */
public class MercadoPagoGateway implements PaymentGateway {

    private static final Logger log = LoggerFactory.getLogger(MercadoPagoGateway.class);
    // ISO-8601 with milliseconds and offset, in Brazil time ("2026-11-10T22:30:00.000-03:00").
    private static final DateTimeFormatter EXPIRATION_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");
    private static final ZoneId BRAZIL = ZoneId.of("America/Sao_Paulo");

    private final RestClient restClient;
    private final MercadoPagoProperties properties;

    public MercadoPagoGateway(RestClient restClient, MercadoPagoProperties properties) {
        this.restClient = restClient;
        this.properties = properties;
    }

    @Override
    public GatewayPayment createPix(PixCharge charge) {
        Map<String, Object> body = baseBody(charge.amount(), charge.description(), charge.externalReference(), charge.payer());
        body.put("payment_method_id", "pix");
        body.put("date_of_expiration", EXPIRATION_FORMAT.format(OffsetDateTime.ofInstant(charge.expiresAt(), BRAZIL)));
        return post("/v1/payments", charge.idempotencyKey(), body);
    }

    @Override
    public GatewayPayment createCard(CardCharge charge) {
        Map<String, Object> body = baseBody(charge.amount(), charge.description(), charge.externalReference(), charge.payer());
        body.put("token", charge.token());
        body.put("payment_method_id", charge.paymentMethodId());
        if (charge.issuerId() != null && !charge.issuerId().isBlank()) {
            body.put("issuer_id", charge.issuerId());
        }
        body.put("installments", charge.installments());
        body.put("three_d_secure_mode", "optional");
        return post("/v1/payments", charge.idempotencyKey(), body);
    }

    @Override
    public GatewayPayment fetch(String paymentId) {
        try {
            return toPayment(restClient.get().uri("/v1/payments/{id}", paymentId).retrieve().body(JsonNode.class));
        } catch (RestClientException exception) {
            throw translate("fetch", exception);
        }
    }

    @Override
    public GatewayPayment refund(String paymentId, String idempotencyKey) {
        try {
            restClient.post().uri("/v1/payments/{id}/refunds", paymentId)
                    .header("X-Idempotency-Key", idempotencyKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of())
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException exception) {
            throw translate("refund", exception);
        }
        return fetch(paymentId);
    }

    @Override
    public String publicKey() {
        return properties.getPublicKey();
    }

    private Map<String, Object> baseBody(BigDecimal amount, String description, String externalReference, Payer payer) {
        Map<String, Object> payerBody = new LinkedHashMap<>();
        payerBody.put("email", payer.email());
        if (payer.identificationNumber() != null) {
            payerBody.put("identification", Map.of("type", payer.identificationType(), "number", payer.identificationNumber()));
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("transaction_amount", amount);
        body.put("description", description);
        body.put("external_reference", externalReference);
        body.put("payer", payerBody);
        if (properties.getNotificationUrl() != null && !properties.getNotificationUrl().isBlank()) {
            body.put("notification_url", properties.getNotificationUrl());
        }
        return body;
    }

    private GatewayPayment post(String path, String idempotencyKey, Map<String, Object> body) {
        try {
            JsonNode response = restClient.post().uri(path)
                    .header("X-Idempotency-Key", idempotencyKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
            return toPayment(response);
        } catch (RestClientException exception) {
            throw translate("create", exception);
        }
    }

    private static PaymentGatewayException translate(String operation, RestClientException exception) {
        if (exception instanceof RestClientResponseException response) {
            int status = response.getStatusCode().value();
            log.warn("Mercado Pago {} failed with status {}", operation, status);
            return new PaymentGatewayException("Payment provider refused the request (status " + status + ")",
                    status >= 500 || status == 429, exception);
        }
        boolean unreachable = exception instanceof ResourceAccessException;
        log.warn("Mercado Pago {} failed: {}", operation, exception.getClass().getSimpleName());
        return new PaymentGatewayException(unreachable ? "Payment provider is unreachable"
                : "Payment provider response could not be processed", true, exception);
    }

    static GatewayPayment toPayment(JsonNode node) {
        if (node == null || node.path("id").isMissingNode()) {
            throw new PaymentGatewayException("Payment provider returned no payment", true, null);
        }
        JsonNode transaction = node.path("point_of_interaction").path("transaction_data");
        JsonNode threeDs = node.path("three_ds_info");
        Challenge challenge = text(threeDs, "external_resource_url") == null ? null
                : new Challenge(text(threeDs, "external_resource_url"), text(threeDs, "creq"));
        String expiration = text(node, "date_of_expiration");
        BigDecimal amount = node.path("transaction_amount").isNumber() ? node.path("transaction_amount").decimalValue() : null;
        return new GatewayPayment(
                node.path("id").asString(),
                text(node, "status"),
                text(node, "status_detail"),
                amount,
                text(node, "external_reference"),
                text(transaction, "qr_code"),
                text(transaction, "qr_code_base64"),
                text(transaction, "ticket_url"),
                expiration == null ? null : OffsetDateTime.parse(expiration).toInstant(),
                challenge);
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        String text = value.asString();
        return text == null || text.isBlank() ? null : text;
    }
}
