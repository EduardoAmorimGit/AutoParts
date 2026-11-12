package com.autoparts_maven.autoparts_maven.order;

import static com.autoparts_maven.autoparts_maven.support.TestAuth.customer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.autoparts_maven.autoparts_maven.delivery.DeliveryDtos.RegionRequest;
import com.autoparts_maven.autoparts_maven.delivery.DeliveryRegionService;
import com.autoparts_maven.autoparts_maven.identity.AuthService;
import com.autoparts_maven.autoparts_maven.identity.dto.IdentityDtos.RegisterRequest;
import com.autoparts_maven.autoparts_maven.product.Product;
import com.autoparts_maven.autoparts_maven.product.ProductRepository;
import com.autoparts_maven.autoparts_maven.product.ProductService;
import com.autoparts_maven.autoparts_maven.product.dto.ProductRequest;
import com.autoparts_maven.autoparts_maven.product.dto.ProductResponse;
import com.autoparts_maven.autoparts_maven.support.CepLookupTestConfig;
import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
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
@Import(CepLookupTestConfig.class)
@Transactional
class CheckoutFlowTest {

    private static final String RIO_ADDRESS = """
            {"recipientName":"Ana Souza","phone":"(21) 99999-1234","cep":"20040-020","street":"Rua do Ouvidor",
             "number":"50","district":"Centro","city":"Rio de Janeiro","state":"rj"}
            """;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AuthService authService;

    @Autowired
    private ProductService productService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private DeliveryRegionService deliveryRegionService;

    @Autowired
    private OrderService orderService;

    private RequestPostProcessor ana;
    private RequestPostProcessor bob;
    private ProductResponse filter;
    private ProductResponse oil;

    @BeforeEach
    void setUp() {
        // Start from no regions (the seeded Rio regions are rolled back with the test transaction).
        jdbcTemplate.update("DELETE FROM delivery_regions");
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        ana = customer(authService.register(new RegisterRequest("ana-" + suffix + "@example.com", "Str0ng-pass", "Ana")).id());
        bob = customer(authService.register(new RegisterRequest("bob-" + suffix + "@example.com", "Str0ng-pass", "Bob")).id());
        deliveryRegionService.create(new RegionRequest("Centro " + suffix, new BigDecimal("15.00"), true,
                List.of("Centro", "Lapa")), null);
        filter = productService.create(new ProductRequest("CK-FLT-" + suffix, "Filtro", null, null, new BigDecimal("30.00"), 5, true));
        oil = productService.create(new ProductRequest("CK-OIL-" + suffix, "Oleo 1L", null, null, new BigDecimal("40.00"), 10, true));
    }

    @Test
    void addressesOutsideRioCityAreRejected() throws Exception {
        mockMvc.perform(post("/api/me/addresses").with(ana).contentType(MediaType.APPLICATION_JSON).content(RIO_ADDRESS))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.cep").value("20040020"))
                .andExpect(jsonPath("$.phone").value("21999991234"))
                .andExpect(jsonPath("$.state").value("RJ"));

        String niteroi = RIO_ADDRESS.replace("20040-020", "24020-000").replace("Rio de Janeiro", "Niteroi");
        mockMvc.perform(post("/api/me/addresses").with(ana).contentType(MediaType.APPLICATION_JSON).content(niteroi))
                .andExpect(status().isUnprocessableContent())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

        String saoPaulo = RIO_ADDRESS.replace("20040-020", "01001-000");
        mockMvc.perform(post("/api/me/addresses").with(ana).contentType(MediaType.APPLICATION_JSON).content(saoPaulo))
                .andExpect(status().isUnprocessableContent());
    }

    @Test
    void districtCityAndStateComeFromTheCepNotFromTheCustomer() throws Exception {
        String typed = RIO_ADDRESS.replace("\"Centro\"", "\"Leblon\"").replace("Rio de Janeiro", "Niteroi")
                .replace("\"rj\"", "\"SP\"");
        mockMvc.perform(post("/api/me/addresses").with(ana).contentType(MediaType.APPLICATION_JSON).content(typed))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.district").value("Centro"))
                .andExpect(jsonPath("$.city").value("Rio de Janeiro"))
                .andExpect(jsonPath("$.state").value("RJ"))
                .andExpect(jsonPath("$.delivery.fee").value(15.0));

        String noDistrict = RIO_ADDRESS.replace("20040-020", "20000-999").replace("\"Centro\"", "\"Lapa\"");
        mockMvc.perform(post("/api/me/addresses").with(ana).contentType(MediaType.APPLICATION_JSON).content(noDistrict))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.district").value("Lapa"));

        mockMvc.perform(post("/api/me/addresses").with(ana).contentType(MediaType.APPLICATION_JSON)
                        .content(RIO_ADDRESS.replace("20040-020", "99999-999")))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.title").value("CEP not found"));
        mockMvc.perform(post("/api/me/addresses").with(ana).contentType(MediaType.APPLICATION_JSON)
                        .content(RIO_ADDRESS.replace("20040-020", "20999-000")))
                .andExpect(status().isServiceUnavailable());
        mockMvc.perform(get("/api/me/addresses").with(ana)).andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void cartComputesTotalsAndRejectsUnavailableQuantities() throws Exception {
        setQuantity(ana, filter.id(), 2)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subtotal").value(60.0))
                .andExpect(jsonPath("$.deliveryFee").doesNotExist())
                .andExpect(jsonPath("$.total").doesNotExist())
                .andExpect(jsonPath("$.readyForCheckout").value(true));

        Long addressId = createAddress(ana);
        mockMvc.perform(get("/api/cart").param("addressId", addressId.toString()).with(ana))
                .andExpect(jsonPath("$.deliveryRegion").value(org.hamcrest.Matchers.startsWith("Centro")))
                .andExpect(jsonPath("$.deliveryFee").value(15.0))
                .andExpect(jsonPath("$.total").value(75.0))
                .andExpect(jsonPath("$.readyForCheckout").value(true));
        mockMvc.perform(get("/api/cart").param("addressId", addressId.toString()).with(bob))
                .andExpect(status().isNotFound());

        setQuantity(ana, filter.id(), 6)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.available").value(5));

        setQuantity(ana, filter.id(), 0)
                .andExpect(jsonPath("$.itemCount").value(0))
                .andExpect(jsonPath("$.readyForCheckout").value(false));

        ProductResponse inactive = productService.create(
                new ProductRequest("CK-INACTIVE-" + filter.id(), "Off", null, null, BigDecimal.ONE, 5, false));
        setQuantity(ana, inactive.id(), 1).andExpect(status().isNotFound());

        mockMvc.perform(get("/api/cart")).andExpect(status().isUnauthorized());
    }

    @Test
    void checkoutReservesStockSnapshotsPricesAndEmptiesTheCart() throws Exception {
        Long addressId = createAddress(ana);
        setQuantity(ana, filter.id(), 2);
        setQuantity(ana, oil.id(), 4);

        String body = checkout(ana, addressId, "key-" + UUID.randomUUID())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("AWAITING_PAYMENT"))
                .andExpect(jsonPath("$.subtotal").value(220.0))
                .andExpect(jsonPath("$.deliveryFee").value(15.0))
                .andExpect(jsonPath("$.deliveryRegion").value(org.hamcrest.Matchers.startsWith("Centro")))
                .andExpect(jsonPath("$.total").value(235.0))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.deliveryAddress.street").value("Rua do Ouvidor"))
                .andReturn().getResponse().getContentAsString();
        Number orderId = JsonPath.read(body, "$.id");

        assertThat(stockOf(filter.id())).isEqualTo(3);
        assertThat(stockOf(oil.id())).isEqualTo(6);
        mockMvc.perform(get("/api/cart").with(ana)).andExpect(jsonPath("$.itemCount").value(0));

        productService.update(filter.id(), new ProductRequest(filter.sku(), "Filtro novo nome", null, null,
                new BigDecimal("99.00"), 3, true));
        mockMvc.perform(get("/api/orders/{id}", orderId).with(ana))
                .andExpect(jsonPath("$.items[0].unitPrice").value(30.0))
                .andExpect(jsonPath("$.items[0].productName").value("Filtro"))
                .andExpect(jsonPath("$.total").value(235.0));
    }

    @Test
    void sameIdempotencyKeyNeverCreatesASecondOrder() throws Exception {
        Long addressId = createAddress(ana);
        setQuantity(ana, filter.id(), 1);
        String key = "key-" + UUID.randomUUID();

        Number first = JsonPath.read(checkout(ana, addressId, key).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.id");
        Number second = JsonPath.read(checkout(ana, addressId, key).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.id");

        assertThat(second).isEqualTo(first);
        assertThat(stockOf(filter.id())).isEqualTo(4);
        mockMvc.perform(post("/api/checkout").with(ana).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"addressId\":" + addressId + "}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void checkoutFailsWithoutChangesWhenStockRanOut() throws Exception {
        Long addressId = createAddress(ana);
        setQuantity(ana, filter.id(), 2);
        setQuantity(ana, oil.id(), 3);
        Product product = productRepository.findById(oil.id()).orElseThrow();
        product.setStockQuantity(1);
        productRepository.flush();

        checkout(ana, addressId, "key-" + UUID.randomUUID())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.sku").value(oil.sku()));

        mockMvc.perform(get("/api/cart").with(ana))
                .andExpect(jsonPath("$.itemCount").value(5))
                .andExpect(jsonPath("$.readyForCheckout").value(false));
    }

    @Test
    void checkoutNeedsACartAnOwnAddressAndAServedDistrict() throws Exception {
        Long anasAddress = createAddress(ana);
        checkout(ana, anasAddress, "key-" + UUID.randomUUID()).andExpect(status().isBadRequest());

        setQuantity(bob, filter.id(), 1);
        checkout(bob, anasAddress, "key-" + UUID.randomUUID()).andExpect(status().isNotFound());

        Number bangu = JsonPath.read(mockMvc.perform(post("/api/me/addresses").with(ana)
                        .contentType(MediaType.APPLICATION_JSON).content(RIO_ADDRESS.replace("20040-020", "21810-000")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.delivery").doesNotExist())
                .andReturn().getResponse().getContentAsString(), "$.id");
        setQuantity(ana, filter.id(), 1);
        mockMvc.perform(get("/api/cart").param("addressId", bangu.toString()).with(ana))
                .andExpect(jsonPath("$.deliveryFee").doesNotExist())
                .andExpect(jsonPath("$.readyForCheckout").value(false));
        checkout(ana, bangu.longValue(), "key-" + UUID.randomUUID())
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.district").value("Bangu"));
        assertThat(stockOf(filter.id())).isEqualTo(5);
    }

    @Test
    void customerCancelsAwaitingOrderAndStockReturns() throws Exception {
        Number orderId = placeOrder(ana, filter.id(), 2);
        assertThat(stockOf(filter.id())).isEqualTo(3);

        mockMvc.perform(post("/api/orders/{id}/cancel", orderId).with(ana))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.cancelReason").value("Cancelado pelo cliente"));
        assertThat(stockOf(filter.id())).isEqualTo(5);

        mockMvc.perform(post("/api/orders/{id}/cancel", orderId).with(ana)).andExpect(status().isConflict());
    }

    @Test
    void unpaidOrdersExpireAndReturnStock() throws Exception {
        Number orderId = placeOrder(ana, oil.id(), 3);
        assertThat(stockOf(oil.id())).isEqualTo(7);

        assertThat(orderService.expireOverdue(Instant.now().plus(Duration.ofMinutes(5)), 100)).isZero();
        int expired = orderService.expireOverdue(Instant.now().plus(Duration.ofMinutes(31)), 100);

        assertThat(expired).isGreaterThanOrEqualTo(1);
        mockMvc.perform(get("/api/orders/{id}", orderId).with(ana))
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.cancelReason").value(OrderService.EXPIRED_REASON));
        assertThat(stockOf(oil.id())).isEqualTo(10);
    }

    @Test
    void customersOnlySeeTheirOwnOrdersAndAdminsSeeAll() throws Exception {
        Number orderId = placeOrder(ana, filter.id(), 1);

        mockMvc.perform(get("/api/orders").with(ana))
                .andExpect(jsonPath("$.content[0].id").value(orderId))
                .andExpect(jsonPath("$.content[0].itemCount").value(1));
        mockMvc.perform(get("/api/orders/{id}", orderId).with(bob)).andExpect(status().isNotFound());
        mockMvc.perform(post("/api/orders/{id}/cancel", orderId).with(bob)).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/admin/orders").with(bob)).andExpect(status().isForbidden());

        mockMvc.perform(get("/api/admin/orders").param("status", "AWAITING_PAYMENT")
                        .with(com.autoparts_maven.autoparts_maven.support.TestAuth.admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.id == " + orderId + ")]").isNotEmpty());
    }

    @Test
    void addressesBelongToTheirOwner() throws Exception {
        Long addressId = createAddress(ana);

        mockMvc.perform(get("/api/me/addresses").with(bob)).andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(put("/api/me/addresses/{id}", addressId).with(bob)
                        .contentType(MediaType.APPLICATION_JSON).content(RIO_ADDRESS))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/me/addresses/{id}", addressId).with(bob)).andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/me/addresses/{id}", addressId).with(ana)).andExpect(status().isNoContent());
    }

    private Number placeOrder(RequestPostProcessor user, Long productId, int quantity) throws Exception {
        Long addressId = createAddress(user);
        setQuantity(user, productId, quantity).andExpect(status().isOk());
        return JsonPath.read(checkout(user, addressId, "key-" + UUID.randomUUID())
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
    }

    private Long createAddress(RequestPostProcessor user) throws Exception {
        Number id = JsonPath.read(mockMvc.perform(post("/api/me/addresses").with(user)
                        .contentType(MediaType.APPLICATION_JSON).content(RIO_ADDRESS))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
        return id.longValue();
    }

    private ResultActions setQuantity(RequestPostProcessor user, Long productId, int quantity) throws Exception {
        return mockMvc.perform(put("/api/cart/items/{id}", productId).with(user)
                .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":" + quantity + "}"));
    }

    private ResultActions checkout(RequestPostProcessor user, Long addressId, String key) throws Exception {
        return mockMvc.perform(post("/api/checkout").with(user).header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON).content("{\"addressId\":" + addressId + "}"));
    }

    private int stockOf(Long productId) {
        productRepository.flush();
        return productRepository.findById(productId).orElseThrow().getStockQuantity();
    }
}
