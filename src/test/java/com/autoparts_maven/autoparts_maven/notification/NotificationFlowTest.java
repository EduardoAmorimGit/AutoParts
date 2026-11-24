package com.autoparts_maven.autoparts_maven.notification;

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
import com.autoparts_maven.autoparts_maven.product.ProductService;
import com.autoparts_maven.autoparts_maven.product.dto.ProductRequest;
import com.autoparts_maven.autoparts_maven.support.CepLookupTestConfig;
import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({CepLookupTestConfig.class, NotificationFlowTest.CapturingSenderConfig.class})
@Transactional
class NotificationFlowTest {

    /** Records sent e-mails; fails while {@code failing} is set. */
    static class CapturingSender implements EmailSender {
        final List<OutgoingEmail> sent = new ArrayList<>();
        boolean failing;

        @Override
        public void send(OutgoingEmail email) {
            if (failing) {
                throw new EmailSendException("SMTP send failed: MailSendException", null);
            }
            sent.add(email);
        }
    }

    @TestConfiguration
    static class CapturingSenderConfig {
        @Bean
        @Primary
        CapturingSender capturingSender() {
            return new CapturingSender();
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AuthService authService;

    @Autowired
    private ProductService productService;

    @Autowired
    private DeliveryRegionService deliveryRegionService;

    @Autowired
    private EmailOutboxRepository outboxRepository;

    @Autowired
    private EmailOutboxService outboxService;

    @Autowired
    private CapturingSender sender;

    private String email;
    private RequestPostProcessor ana;
    private Long productId;
    private Long addressId;

    @BeforeEach
    void setUp() throws Exception {
        sender.sent.clear();
        sender.failing = false;
        jdbcTemplate.update("DELETE FROM delivery_regions");
        // Earlier tests may have left committed e-mails behind; only this test's ones matter.
        jdbcTemplate.update("UPDATE email_outbox SET status = 'SENT' WHERE status = 'PENDING'");
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        email = "mail-" + suffix + "@example.com";
        ana = customer(authService.register(new RegisterRequest(email, "Str0ng-pass", "Ana Souza")).id());
        deliveryRegionService.create(new RegionRequest("Centro " + suffix, new BigDecimal("15.00"), true,
                List.of("Centro")), null);
        productId = productService.create(
                new ProductRequest("MAIL-" + suffix, "Filtro de oleo", null, null, new BigDecimal("35.00"), 5, true)).id();
        addressId = ((Number) JsonPath.read(mockMvc.perform(post("/api/me/addresses").with(ana)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"recipientName":"Ana","phone":"21999991234","cep":"20040020","street":"Rua A",
                                 "number":"1","district":"Centro","city":"Rio de Janeiro","state":"RJ"}"""))
                .andReturn().getResponse().getContentAsString(), "$.id")).longValue();
    }

    @Test
    void placingAndCancellingAnOrderQueuesEmailsThatTheJobSends() throws Exception {
        Long orderId = placeOrder();
        mockMvc.perform(post("/api/orders/{id}/cancel", orderId).with(ana)).andExpect(status().isOk());

        List<EmailOutbox> queued = outboxRepository.findByOrderIdOrderByIdAsc(orderId);
        assertThat(queued).extracting(EmailOutbox::getKind).containsExactly("ORDER_PLACED", "ORDER_CANCELLED");
        assertThat(queued).allSatisfy(e -> {
            assertThat(e.getToAddress()).isEqualTo(email);
            assertThat(e.getStatus()).isEqualTo(EmailOutbox.Status.PENDING);
        });
        assertThat(queued.get(0).getSubject()).isEqualTo("Recebemos seu pedido #" + orderId);
        assertThat(queued.get(0).getBody()).contains("Olá, Ana Souza!", "Filtro de oleo", "Total: R$ 50,00",
                "CNPJ [A PREENCHER]");
        assertThat(queued.get(1).getBody()).contains("Motivo: Cancelado pelo cliente",
                "Nenhum valor foi cobrado");

        assertThat(outboxService.dispatchDue(Instant.now(), 50)).isEqualTo(2);
        assertThat(sender.sent).extracting(EmailSender.OutgoingEmail::to).containsOnly(email);
        assertThat(outboxRepository.findByOrderIdOrderByIdAsc(orderId))
                .allSatisfy(e -> {
                    assertThat(e.getStatus()).isEqualTo(EmailOutbox.Status.SENT);
                    assertThat(e.getSentAt()).isNotNull();
                });
        assertThat(outboxService.dispatchDue(Instant.now(), 50)).isZero();
    }

    @Test
    void failedSendsAreRetriedWithBackoffAndEventuallyGiveUp() throws Exception {
        Long orderId = placeOrder();
        sender.failing = true;

        Instant now = Instant.now();
        assertThat(outboxService.dispatchDue(now, 50)).isZero();
        EmailOutbox entry = outboxRepository.findByOrderIdOrderByIdAsc(orderId).get(0);
        assertThat(entry.getStatus()).isEqualTo(EmailOutbox.Status.PENDING);
        assertThat(entry.getAttempts()).isEqualTo(1);
        assertThat(entry.getLastError()).isEqualTo("SMTP send failed: MailSendException");
        assertThat(entry.getNextAttemptAt()).isAfter(now);

        for (int attempt = 2; attempt <= 5; attempt++) {
            outboxService.dispatchDue(Instant.now().plusSeconds(3600L * attempt), 50);
        }
        assertThat(outboxRepository.findByOrderIdOrderByIdAsc(orderId).get(0).getStatus())
                .isEqualTo(EmailOutbox.Status.FAILED);

        sender.failing = false;
        outboxService.dispatchDue(Instant.now().plusSeconds(86400), 50);
        assertThat(sender.sent).isEmpty();

        Long emailId = outboxRepository.findByOrderIdOrderByIdAsc(orderId).get(0).getId();
        mockMvc.perform(get("/api/admin/emails").with(admin()))
                .andExpect(jsonPath("$.content[?(@.id == " + emailId + ")].lastError")
                        .value("SMTP send failed: MailSendException"));
        mockMvc.perform(post("/api/admin/emails/{id}/retry", emailId).with(ana)).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/admin/emails/{id}/retry", emailId).with(admin()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PENDING"));
        mockMvc.perform(post("/api/admin/emails/{id}/retry", emailId).with(admin())).andExpect(status().isConflict());
        assertThat(outboxService.dispatchDue(Instant.now().plusSeconds(1), 50)).isEqualTo(1);
        mockMvc.perform(get("/api/admin/emails/orders/{id}", orderId).with(admin()))
                .andExpect(jsonPath("$[0].status").value("SENT"));
    }

    private Long placeOrder() throws Exception {
        mockMvc.perform(put("/api/cart/items/{id}", productId).with(ana)
                .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":1}")).andExpect(status().isOk());
        Number id = JsonPath.read(mockMvc.perform(post("/api/checkout").with(ana)
                        .header("Idempotency-Key", "key-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"addressId\":" + addressId + ",\"acceptedTermsVersion\":\"test-terms\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
        return id.longValue();
    }
}
