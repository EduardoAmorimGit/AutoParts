package com.autoparts_maven.autoparts_maven.payment;

import static com.autoparts_maven.autoparts_maven.support.TestAuth.admin;
import static com.autoparts_maven.autoparts_maven.support.TestAuth.customer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.autoparts_maven.autoparts_maven.identity.AuthService;
import com.autoparts_maven.autoparts_maven.identity.dto.IdentityDtos.RegisterRequest;
import com.autoparts_maven.autoparts_maven.order.OrderService;
import com.autoparts_maven.autoparts_maven.product.ProductService;
import com.autoparts_maven.autoparts_maven.product.dto.ProductRequest;
import com.autoparts_maven.autoparts_maven.product.dto.ProductResponse;
import com.autoparts_maven.autoparts_maven.store.StoreSettingsService;
import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(FakePaymentGateway.Config.class)
@Transactional
class PaymentFlowTest {

    private static final String WEBHOOK_SECRET = "test-webhook-secret";
    private static final String CARD = """
            {"token":"card-token","paymentMethodId":"master","issuerId":"24","installments":1,
             "identificationType":"CPF","identificationNumber":"123.456.789-09"}
            """;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private FakePaymentGateway gateway;

    @Autowired
    private AuthService authService;

    @Autowired
    private ProductService productService;

    @Autowired
    private StoreSettingsService storeSettingsService;

    @Autowired
    private OrderService orderService;

    private RequestPostProcessor ana;
    private RequestPostProcessor bob;
    private Long orderId;

    @BeforeEach
    void placeOrder() throws Exception {
        gateway.reset();
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        ana = customer(authService.register(new RegisterRequest("pay-ana-" + suffix + "@example.com", "Str0ng-pass", "Ana")).id());
        bob = customer(authService.register(new RegisterRequest("pay-bob-" + suffix + "@example.com", "Str0ng-pass", "Bob")).id());
        storeSettingsService.updateDeliveryFee(new BigDecimal("15.00"), null);
        ProductResponse product = productService.create(
                new ProductRequest("PAY-" + suffix, "Filtro", null, null, new BigDecimal("35.00"), 5, true));

        Number addressId = JsonPath.read(mockMvc.perform(post("/api/me/addresses").with(ana)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"recipientName":"Ana","phone":"21999991234","cep":"20040020","street":"Rua A",
                                 "number":"1","district":"Centro","city":"Rio de Janeiro","state":"RJ"}"""))
                .andReturn().getResponse().getContentAsString(), "$.id");
        mockMvc.perform(put("/api/cart/items/{id}", product.id()).with(ana)
                .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":1}")).andExpect(status().isOk());
        Number id = JsonPath.read(mockMvc.perform(post("/api/checkout").with(ana)
                        .header("Idempotency-Key", "key-" + suffix)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"addressId\":" + addressId + "}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
        orderId = id.longValue();
    }

    @Test
    void pixReturnsQrCodeReusesTheOpenChargeAndKeepsTheOrderAlive() throws Exception {
        Instant dueBefore = orderService.lockForPayment(orderId).paymentDueAt();

        pix(ana).andExpect(status().isOk())
                .andExpect(jsonPath("$.method").value("PIX"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.amount").value(50.0))
                .andExpect(jsonPath("$.pix.qrCode").value("00020126-fake-pix"))
                .andExpect(jsonPath("$.pix.qrCodeBase64").value("iVBORw0KGgo="));
        pix(ana).andExpect(status().isOk());

        assertThat(gateway.createCalls).isEqualTo(1);
        Instant dueAfter = orderService.lockForPayment(orderId).paymentDueAt();
        assertThat(dueAfter).isAfter(dueBefore);
        assertThat(dueAfter).isAfter(Instant.now().plus(Duration.ofMinutes(31)));
    }

    @Test
    void approvedPixNotificationPaysTheOrder() throws Exception {
        String providerId = JsonPath.read(pix(ana).andReturn().getResponse().getContentAsString(), "$.id").toString();
        String mpId = providerIdOf(providerId);
        gateway.change(mpId, "approved", "accredited");

        webhook(mpId, true).andExpect(status().isOk());

        mockMvc.perform(get("/api/orders/{id}", orderId).with(ana)).andExpect(jsonPath("$.status").value("PAID"));
        mockMvc.perform(get("/api/orders/{id}/payments", orderId).with(ana))
                .andExpect(jsonPath("$[0].status").value("APPROVED"))
                .andExpect(jsonPath("$[0].approvedAt").isNotEmpty());
        pix(ana).andExpect(status().isConflict());
    }

    @Test
    void notificationsWithABadSignatureAreRejectedAndUnknownPaymentsIgnored() throws Exception {
        webhook("424242", false).andExpect(status().isUnauthorized());
        webhook("424242", true).andExpect(status().isOk());
        mockMvc.perform(get("/api/orders/{id}", orderId).with(ana))
                .andExpect(jsonPath("$.status").value("AWAITING_PAYMENT"));
    }

    @Test
    void approvedCardPaysImmediatelyAndRejectedCardAllowsRetry() throws Exception {
        gateway.nextStatus = "rejected";
        gateway.nextStatusDetail = "cc_rejected_insufficient_amount";
        card(ana).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.statusDetail").value("cc_rejected_insufficient_amount"));
        mockMvc.perform(get("/api/orders/{id}", orderId).with(ana))
                .andExpect(jsonPath("$.status").value("AWAITING_PAYMENT"));

        gateway.nextStatus = "approved";
        gateway.nextStatusDetail = "accredited";
        card(ana).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("APPROVED"));
        mockMvc.perform(get("/api/orders/{id}", orderId).with(ana)).andExpect(jsonPath("$.status").value("PAID"));
    }

    @Test
    void threeDsChallengeIsReturnedAndSyncCompletesThePayment() throws Exception {
        gateway.nextStatusDetail = "pending_challenge";
        gateway.nextChallenge = new com.autoparts_maven.autoparts_maven.payment.gateway.PaymentGateway.Challenge(
                "https://acs.example/challenge", "eyJ0");
        String body = card(ana).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.challenge.url").value("https://acs.example/challenge"))
                .andReturn().getResponse().getContentAsString();
        Number paymentId = JsonPath.read(body, "$.id");
        gateway.change(providerIdOf(paymentId.toString()), "approved", "accredited");

        mockMvc.perform(post("/api/orders/{o}/payments/{p}/sync", orderId, paymentId).with(ana))
                .andExpect(jsonPath("$.status").value("APPROVED"));
        mockMvc.perform(get("/api/orders/{id}", orderId).with(ana)).andExpect(jsonPath("$.status").value("PAID"));
    }

    @Test
    void paymentApprovedAfterCancellationIsRefundedAndFlagged() throws Exception {
        String paymentId = JsonPath.read(pix(ana).andReturn().getResponse().getContentAsString(), "$.id").toString();
        String mpId = providerIdOf(paymentId);
        mockMvc.perform(post("/api/orders/{id}/cancel", orderId).with(ana)).andExpect(status().isOk());
        gateway.change(mpId, "approved", "accredited");

        webhook(mpId, true).andExpect(status().isOk());

        assertThat(gateway.refunded).containsExactly(mpId);
        mockMvc.perform(get("/api/orders/{id}", orderId).with(ana)).andExpect(jsonPath("$.status").value("CANCELLED"));
        mockMvc.perform(get("/api/admin/payments/attention").with(admin()))
                .andExpect(jsonPath("$.content[?(@.id == " + paymentId + ")].attentionReason").value(
                        "Payment approved after the order was cancelled; refunded automatically"));
    }

    @Test
    void approvalWithADifferentAmountDoesNotPayTheOrder() throws Exception {
        gateway.amountOverride = new BigDecimal("1.00");
        gateway.nextStatus = "approved";
        card(ana).andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.needsAttention").value(true));
        mockMvc.perform(get("/api/orders/{id}", orderId).with(ana))
                .andExpect(jsonPath("$.status").value("AWAITING_PAYMENT"));
    }

    @Test
    void onlyTheOwnerPaysAndOnlyOpenOrders() throws Exception {
        pix(bob).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/orders/{id}/payments", orderId).with(bob)).andExpect(status().isNotFound());

        mockMvc.perform(post("/api/orders/{id}/cancel", orderId).with(ana)).andExpect(status().isOk());
        pix(ana).andExpect(status().isConflict());
        card(ana).andExpect(status().isConflict());
        assertThat(gateway.createCalls).isZero();
    }

    @Test
    void installmentsAboveTheLimitAndPublicConfig() throws Exception {
        mockMvc.perform(post("/api/orders/{id}/payments/card", orderId).with(ana)
                        .contentType(MediaType.APPLICATION_JSON).content(CARD.replace("\"installments\":1", "\"installments\":3")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/payments/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.publicKey").value("TEST-fake-public-key"));
    }

    private String providerIdOf(String localPaymentId) {
        return gateway.providerIdFor(localPaymentId);
    }

    private ResultActions pix(RequestPostProcessor user) throws Exception {
        return mockMvc.perform(post("/api/orders/{id}/payments/pix", orderId).with(user));
    }

    private ResultActions card(RequestPostProcessor user) throws Exception {
        return mockMvc.perform(post("/api/orders/{id}/payments/card", orderId).with(user)
                .contentType(MediaType.APPLICATION_JSON).content(CARD));
    }

    private ResultActions webhook(String providerPaymentId, boolean validSignature) throws Exception {
        String ts = String.valueOf(Instant.now().getEpochSecond());
        String requestId = UUID.randomUUID().toString();
        String manifest = "id:" + providerPaymentId + ";request-id:" + requestId + ";ts:" + ts + ";";
        String v1 = validSignature ? hmac(manifest) : "0".repeat(64);
        return mockMvc.perform(post("/api/payments/webhooks/mercadopago")
                .param("data.id", providerPaymentId).param("type", "payment")
                .header("x-signature", "ts=" + ts + ",v1=" + v1)
                .header("x-request-id", requestId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"payment\",\"action\":\"payment.updated\",\"data\":{\"id\":\"" + providerPaymentId + "\"}}"));
    }

    private static String hmac(String manifest) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(WEBHOOK_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(manifest.getBytes(StandardCharsets.UTF_8)));
    }
}
