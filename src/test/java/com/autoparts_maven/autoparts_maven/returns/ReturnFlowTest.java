package com.autoparts_maven.autoparts_maven.returns;

import static com.autoparts_maven.autoparts_maven.support.TestAuth.admin;
import static com.autoparts_maven.autoparts_maven.support.TestAuth.customer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.autoparts_maven.autoparts_maven.delivery.DeliveryDtos.RegionRequest;
import com.autoparts_maven.autoparts_maven.delivery.DeliveryRegionService;
import com.autoparts_maven.autoparts_maven.identity.AuthService;
import com.autoparts_maven.autoparts_maven.identity.dto.IdentityDtos.RegisterRequest;
import com.autoparts_maven.autoparts_maven.notification.EmailOutbox;
import com.autoparts_maven.autoparts_maven.notification.EmailOutboxRepository;
import com.autoparts_maven.autoparts_maven.payment.FakePaymentGateway;
import com.autoparts_maven.autoparts_maven.product.ProductRepository;
import com.autoparts_maven.autoparts_maven.product.ProductService;
import com.autoparts_maven.autoparts_maven.product.dto.ProductRequest;
import com.autoparts_maven.autoparts_maven.support.CepLookupTestConfig;
import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({FakePaymentGateway.Config.class, CepLookupTestConfig.class})
@Transactional
class ReturnFlowTest {

    private static final String CARD = """
            {"token":"card-token","paymentMethodId":"master","issuerId":"24","installments":1,
             "identificationType":"CPF","identificationNumber":"123.456.789-09"}
            """;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private FakePaymentGateway gateway;

    @Autowired
    private AuthService authService;

    @Autowired
    private ProductService productService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private DeliveryRegionService deliveryRegionService;

    @Autowired
    private EmailOutboxRepository outboxRepository;

    private RequestPostProcessor ana;
    private RequestPostProcessor bob;
    private Long productId;
    private Long orderId;
    private Long orderItemId;
    private String providerPaymentId;

    @BeforeEach
    void deliverAnOrderOfTwoUnits() throws Exception {
        jdbcTemplate.update("DELETE FROM delivery_regions");
        gateway.reset();
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        ana = customer(authService.register(new RegisterRequest("ret-ana-" + suffix + "@example.com", "Str0ng-pass", "Ana")).id());
        bob = customer(authService.register(new RegisterRequest("ret-bob-" + suffix + "@example.com", "Str0ng-pass", "Bob")).id());
        deliveryRegionService.create(new RegionRequest("Centro " + suffix, new BigDecimal("15.00"), true,
                List.of("Centro")), null);
        productId = productService.create(
                new ProductRequest("RET-" + suffix, "Filtro", null, null, new BigDecimal("35.00"), 5, true)).id();

        Number addressId = JsonPath.read(mockMvc.perform(post("/api/me/addresses").with(ana)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"recipientName":"Ana","phone":"21999991234","cep":"20040020","street":"Rua A",
                                 "number":"1","district":"Centro","city":"Rio de Janeiro","state":"RJ"}"""))
                .andReturn().getResponse().getContentAsString(), "$.id");
        mockMvc.perform(put("/api/cart/items/{id}", productId).with(ana)
                .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":2}")).andExpect(status().isOk());
        String order = mockMvc.perform(post("/api/checkout").with(ana).header("Idempotency-Key", "key-" + suffix)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"addressId\":" + addressId + ",\"acceptedTermsVersion\":\"test-terms\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        orderId = ((Number) JsonPath.read(order, "$.id")).longValue();
        orderItemId = ((Number) JsonPath.read(order, "$.items[0].id")).longValue();

        gateway.nextStatus = "approved";
        String payment = mockMvc.perform(post("/api/orders/{id}/payments/card", orderId).with(ana)
                        .contentType(MediaType.APPLICATION_JSON).content(CARD))
                .andExpect(jsonPath("$.status").value("APPROVED")).andReturn().getResponse().getContentAsString();
        providerPaymentId = gateway.providerIdFor(JsonPath.read(payment, "$.id").toString());

        mockMvc.perform(post("/api/admin/orders/{id}/prepare", orderId).with(admin())).andExpect(status().isOk());
        mockMvc.perform(post("/api/admin/orders/{id}/ship", orderId).with(admin())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"trackingUrl\":\"https://share.lalamove.com/r\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/admin/orders/{id}/deliver", orderId).with(admin())).andExpect(status().isOk());
    }

    @Test
    void withdrawalRefundsItemsAndTheDeliveryFeeOnceAndCannotExceedWhatWasBought() throws Exception {
        String first = requestReturn(ana, "WITHDRAWAL", null, 1)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("REQUESTED"))
                .andExpect(jsonPath("$.itemsAmount").value(35.0))
                .andExpect(jsonPath("$.deliveryFeeAmount").value(15.0))
                .andExpect(jsonPath("$.refundAmount").value(50.0))
                .andReturn().getResponse().getContentAsString();
        Long firstId = ((Number) JsonPath.read(first, "$.id")).longValue();
        int shelf = shelfStock();

        mockMvc.perform(post("/api/admin/returns/{id}/approve", firstId).with(admin())).andExpect(status().isOk());
        mockMvc.perform(post("/api/admin/returns/{id}/receive", firstId).with(admin())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"restock\":true}"))
                .andExpect(jsonPath("$.restocked").value(true));
        assertThat(shelfStock()).isEqualTo(shelf + 1);
        mockMvc.perform(post("/api/admin/returns/{id}/refund", firstId).with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REFUNDED"));
        assertThat(gateway.partialRefunds).containsExactly(providerPaymentId + ":50.00");
        mockMvc.perform(post("/api/admin/returns/{id}/refund", firstId).with(admin())).andExpect(status().isConflict());
        mockMvc.perform(get("/api/orders/{id}", orderId).with(ana)).andExpect(jsonPath("$.refundedAmount").value(50.0));

        requestReturn(ana, "WITHDRAWAL", null, 1)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.deliveryFeeAmount").value(0))
                .andExpect(jsonPath("$.refundAmount").value(35.0));
        requestReturn(ana, "WITHDRAWAL", null, 1).andExpect(status().isUnprocessableContent());

        assertThat(outboxRepository.findByOrderIdOrderByIdAsc(orderId)).extracting(EmailOutbox::getKind)
                .contains("RETURN_REQUESTED", "RETURN_APPROVED", "RETURN_RECEIVED", "PAYMENT_REFUNDED");
    }

    @Test
    void withdrawalWindowEndsAfterSevenDaysButDefectsCanStillBeReported() throws Exception {
        entityManager.flush();
        jdbcTemplate.update("UPDATE orders SET delivered_at = ? WHERE id = ?",
                java.sql.Timestamp.from(Instant.now().minusSeconds(9L * 24 * 3600)), orderId);
        entityManager.clear();

        requestReturn(ana, "WITHDRAWAL", null, 1).andExpect(status().isUnprocessableContent());
        requestReturn(ana, "DEFECT", null, 1).andExpect(status().isUnprocessableContent());
        String defect = requestReturn(ana, "DEFECT", "Filtro vazando na rosca", 1)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.deliveryFeeAmount").value(15.0))
                .andExpect(jsonPath("$.refundAmount").value(50.0))
                .andExpect(jsonPath("$.legalDeadline").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        Long defectId = ((Number) JsonPath.read(defect, "$.id")).longValue();

        mockMvc.perform(post("/api/admin/returns/{id}/reject", defectId).with(admin())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/admin/returns/{id}/approve", defectId).with(admin())).andExpect(status().isOk());
        mockMvc.perform(post("/api/admin/returns/{id}/replace", defectId).with(admin())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"note\":\"Enviada unidade nova\"}"))
                .andExpect(jsonPath("$.status").value("REPLACED"));
        assertThat(gateway.partialRefunds).isEmpty();
    }

    @Test
    void rejectedRequestsFreeTheirUnitsAndOnlyDefectsCanBeReplaced() throws Exception {
        Long id = ((Number) JsonPath.read(requestReturn(ana, "WITHDRAWAL", null, 2).andReturn().getResponse()
                .getContentAsString(), "$.id")).longValue();
        mockMvc.perform(post("/api/admin/returns/{id}/approve", id).with(admin())).andExpect(status().isOk());
        mockMvc.perform(post("/api/admin/returns/{id}/replace", id).with(admin())).andExpect(status().isUnprocessableContent());
        mockMvc.perform(post("/api/admin/returns/{id}/reject", id).with(admin())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Produto nao foi devolvido\"}"))
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.rejectionReason").value("Produto nao foi devolvido"));

        requestReturn(ana, "WITHDRAWAL", null, 2).andExpect(status().isCreated())
                .andExpect(jsonPath("$.deliveryFeeAmount").value(15.0));
        assertThat(outboxRepository.findByOrderIdOrderByIdAsc(orderId)).extracting(EmailOutbox::getKind)
                .contains("RETURN_REJECTED");
    }

    @Test
    void aFailedRefundKeepsTheRequestOpenAndTheRetryReusesTheSameKey() throws Exception {
        Long id = ((Number) JsonPath.read(requestReturn(ana, "WITHDRAWAL", null, 1).andReturn().getResponse()
                .getContentAsString(), "$.id")).longValue();
        mockMvc.perform(post("/api/admin/returns/{id}/approve", id).with(admin())).andExpect(status().isOk());

        gateway.failRefunds = true;
        mockMvc.perform(post("/api/admin/returns/{id}/refund", id).with(admin())).andExpect(status().isServiceUnavailable());
        mockMvc.perform(get("/api/admin/returns/{id}", id).with(admin())).andExpect(jsonPath("$.status").value("APPROVED"));
        String key = jdbcTemplate.queryForObject("SELECT idempotency_key FROM refunds WHERE return_request_id = ?",
                String.class, id);

        gateway.failRefunds = false;
        mockMvc.perform(post("/api/admin/returns/{id}/refund", id).with(admin()))
                .andExpect(jsonPath("$.status").value("REFUNDED"));
        assertThat(jdbcTemplate.queryForObject("SELECT idempotency_key FROM refunds WHERE return_request_id = ?",
                String.class, id)).isEqualTo(key);
        assertThat(gateway.partialRefunds).hasSize(1);
    }

    @Test
    void returningEverythingRefundsTheWholePaymentWithoutAFalseAlert() throws Exception {
        Long id = ((Number) JsonPath.read(requestReturn(ana, "WITHDRAWAL", null, 2).andReturn().getResponse()
                .getContentAsString(), "$.id")).longValue();
        mockMvc.perform(post("/api/admin/returns/{id}/approve", id).with(admin())).andExpect(status().isOk());
        mockMvc.perform(post("/api/admin/returns/{id}/refund", id).with(admin()))
                .andExpect(jsonPath("$.refundAmount").value(85.0));

        gateway.change(providerPaymentId, "refunded", "refunded");
        webhook(providerPaymentId).andExpect(status().isOk());

        mockMvc.perform(get("/api/admin/payments/attention").with(admin()))
                .andExpect(jsonPath("$.content[?(@.orderId == " + orderId + ")]").isEmpty());
        assertThat(outboxRepository.findByOrderIdOrderByIdAsc(orderId))
                .filteredOn(e -> e.getKind().equals("PAYMENT_REFUNDED")).hasSize(1);
    }

    @Test
    void onlyTheOwnerOfADeliveredOrderCanReturnAndOnlyAdminsDecide() throws Exception {
        requestReturn(bob, "WITHDRAWAL", null, 1).andExpect(status().isNotFound());
        mockMvc.perform(post("/api/orders/{id}/returns", orderId).with(ana).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"WITHDRAWAL\",\"items\":[{\"orderItemId\":999999,\"quantity\":1}]}"))
                .andExpect(status().isUnprocessableContent());
        Long id = ((Number) JsonPath.read(requestReturn(ana, "WITHDRAWAL", null, 1).andReturn().getResponse()
                .getContentAsString(), "$.id")).longValue();
        mockMvc.perform(post("/api/admin/returns/{id}/approve", id).with(ana)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/orders/{id}/returns", orderId).with(ana)).andExpect(jsonPath("$.length()").value(1));
        mockMvc.perform(get("/api/orders/{id}/returns", orderId).with(bob)).andExpect(status().isNotFound());
    }

    private ResultActions requestReturn(RequestPostProcessor user, String type, String reason, int quantity)
            throws Exception {
        String reasonJson = reason == null ? "" : ",\"reason\":\"" + reason + "\"";
        return mockMvc.perform(post("/api/orders/{id}/returns", orderId).with(user)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"" + type + "\"" + reasonJson + ",\"items\":[{\"orderItemId\":" + orderItemId
                        + ",\"quantity\":" + quantity + "}]}"));
    }

    private int shelfStock() {
        productRepository.flush();
        return productRepository.findById(productId).orElseThrow().getStockQuantity();
    }

    private ResultActions webhook(String providerId) throws Exception {
        String ts = String.valueOf(Instant.now().getEpochSecond());
        String requestId = UUID.randomUUID().toString();
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec("test-webhook-secret".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String v1 = HexFormat.of().formatHex(mac.doFinal(
                ("id:" + providerId + ";request-id:" + requestId + ";ts:" + ts + ";").getBytes(StandardCharsets.UTF_8)));
        return mockMvc.perform(post("/api/payments/webhooks/mercadopago")
                .param("data.id", providerId).param("type", "payment")
                .header("x-signature", "ts=" + ts + ",v1=" + v1).header("x-request-id", requestId));
    }
}
