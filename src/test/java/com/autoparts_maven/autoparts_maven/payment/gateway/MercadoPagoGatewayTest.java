package com.autoparts_maven.autoparts_maven.payment.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.autoparts_maven.autoparts_maven.payment.gateway.PaymentGateway.CardCharge;
import com.autoparts_maven.autoparts_maven.payment.gateway.PaymentGateway.GatewayPayment;
import com.autoparts_maven.autoparts_maven.payment.gateway.PaymentGateway.Payer;
import com.autoparts_maven.autoparts_maven.payment.gateway.PaymentGateway.PixCharge;
import java.math.BigDecimal;
import java.time.Instant;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class MercadoPagoGatewayTest {

    private static final String BASE = "https://api.mercadopago.com";
    private static final Payer PAYER = new Payer("ana@example.com", "CPF", "12345678909");

    private MockRestServiceServer server;
    private MercadoPagoGateway gateway;

    @BeforeEach
    void setUp() {
        MercadoPagoProperties properties = new MercadoPagoProperties();
        properties.setPublicKey("TEST-public");
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE).defaultHeader("Authorization", "Bearer TEST-token");
        server = MockRestServiceServer.bindTo(builder).build();
        gateway = new MercadoPagoGateway(builder.build(), properties);
    }

    @Test
    void createsPixWithIdempotencyKeyExpirationAndReference() {
        server.expect(requestTo(BASE + "/v1/payments"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Idempotency-Key", "4f9c0e1a-0000-4000-8000-000000000001"))
                .andExpect(header("Authorization", "Bearer TEST-token"))
                .andExpect(jsonPath("$.payment_method_id").value("pix"))
                .andExpect(jsonPath("$.transaction_amount").value(235.0))
                .andExpect(jsonPath("$.external_reference").value("autoparts-payment-7"))
                .andExpect(jsonPath("$.payer.email").value("ana@example.com"))
                .andExpect(jsonPath("$.payer.identification.number").value("12345678909"))
                .andExpect(jsonPath("$.date_of_expiration").value(
                        Matchers.matchesPattern("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}-03:00")))
                .andRespond(withSuccess("""
                        {"id": 1234567890, "status": "pending", "status_detail": "pending_waiting_transfer",
                         "transaction_amount": 235.00, "external_reference": "autoparts-payment-7",
                         "date_of_expiration": "2026-11-11T10:31:00.000-03:00",
                         "point_of_interaction": {"transaction_data": {
                           "qr_code": "00020126...", "qr_code_base64": "iVBORw0KGgo=",
                           "ticket_url": "https://www.mercadopago.com.br/payments/1234567890/ticket"}}}
                        """, MediaType.APPLICATION_JSON));

        GatewayPayment payment = gateway.createPix(new PixCharge("4f9c0e1a-0000-4000-8000-000000000001",
                "autoparts-payment-7", new BigDecimal("235.00"), "AutoParts - pedido #7", PAYER,
                Instant.parse("2026-11-11T13:31:00Z")));

        assertThat(payment.id()).isEqualTo("1234567890");
        assertThat(payment.status()).isEqualTo("pending");
        assertThat(payment.pixQrCode()).isEqualTo("00020126...");
        assertThat(payment.pixQrCodeBase64()).isEqualTo("iVBORw0KGgo=");
        assertThat(payment.expiresAt()).isEqualTo(Instant.parse("2026-11-11T13:31:00Z"));
        assertThat(payment.amount()).isEqualByComparingTo("235.00");
        server.verify();
    }

    @Test
    void createsCardPaymentWith3dsAndMapsAChallenge() {
        server.expect(requestTo(BASE + "/v1/payments"))
                .andExpect(jsonPath("$.token").value("card-token"))
                .andExpect(jsonPath("$.payment_method_id").value("master"))
                .andExpect(jsonPath("$.issuer_id").value("24"))
                .andExpect(jsonPath("$.installments").value(1))
                .andExpect(jsonPath("$.three_d_secure_mode").value("optional"))
                .andRespond(withSuccess("""
                        {"id": 55, "status": "pending", "status_detail": "pending_challenge",
                         "transaction_amount": 100, "external_reference": "autoparts-payment-8",
                         "three_ds_info": {"external_resource_url": "https://acs.example/challenge", "creq": "eyJ0"}}
                        """, MediaType.APPLICATION_JSON));

        GatewayPayment payment = gateway.createCard(new CardCharge("key-2", "autoparts-payment-8",
                new BigDecimal("100"), "d", PAYER, "card-token", "master", "24", 1));

        assertThat(payment.statusDetail()).isEqualTo("pending_challenge");
        assertThat(payment.challenge().url()).isEqualTo("https://acs.example/challenge");
        assertThat(payment.challenge().creq()).isEqualTo("eyJ0");
        server.verify();
    }

    @Test
    void distinguishesProviderOutagesFromRefusals() {
        server.expect(requestTo(BASE + "/v1/payments/1")).andRespond(withServerError());
        assertThatThrownBy(() -> gateway.fetch("1"))
                .isInstanceOfSatisfying(PaymentGatewayException.class, e -> assertThat(e.isUnavailable()).isTrue());

        server.reset();
        server.expect(requestTo(BASE + "/v1/payments/2")).andRespond(withStatus(HttpStatus.BAD_REQUEST));
        assertThatThrownBy(() -> gateway.fetch("2"))
                .isInstanceOfSatisfying(PaymentGatewayException.class, e -> assertThat(e.isUnavailable()).isFalse());
    }

    @Test
    void refundsWithIdempotencyKeyAndReturnsTheRefreshedPayment() {
        server.expect(requestTo(BASE + "/v1/payments/77/refunds"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Idempotency-Key", "refund-key"))
                .andRespond(withSuccess("{\"id\": 1, \"payment_id\": 77}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE + "/v1/payments/77"))
                .andRespond(withSuccess("{\"id\": 77, \"status\": \"refunded\", \"transaction_amount\": 10}",
                        MediaType.APPLICATION_JSON));

        assertThat(gateway.refund("77", "refund-key").status()).isEqualTo("refunded");
        server.verify();
    }
}
