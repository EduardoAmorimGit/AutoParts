package com.autoparts_maven.autoparts_maven.delivery;

import static com.autoparts_maven.autoparts_maven.support.TestAuth.customer;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.autoparts_maven.autoparts_maven.identity.AuthService;
import com.autoparts_maven.autoparts_maven.identity.dto.IdentityDtos.RegisterRequest;
import com.autoparts_maven.autoparts_maven.product.ProductService;
import com.autoparts_maven.autoparts_maven.product.dto.ProductRequest;
import com.autoparts_maven.autoparts_maven.product.dto.ProductResponse;
import com.autoparts_maven.autoparts_maven.support.CepLookupTestConfig;
import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
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
class DeliveryRegionAdminTest {

    private static final String ADDRESS = """
            {"recipientName":"Ana Souza","phone":"21999991234","cep":"%s","street":"Rua X",
             "number":"10","district":"Qualquer","city":"Rio de Janeiro","state":"RJ"}
            """;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AuthService authService;

    @Autowired
    private ProductService productService;

    private RequestPostProcessor admin;
    private RequestPostProcessor ana;
    private String suffix;

    @BeforeEach
    void setUp() {
        // Start from no regions (the seeded Rio regions are rolled back with the test transaction).
        jdbcTemplate.update("DELETE FROM delivery_regions");
        suffix = UUID.randomUUID().toString().substring(0, 8);
        Long adminId = authService.register(new RegisterRequest("adm-" + suffix + "@example.com", "Str0ng-pass", "Adm")).id();
        admin = jwt().jwt(token -> token.subject(String.valueOf(adminId)))
                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
        ana = customer(authService.register(new RegisterRequest("reg-ana-" + suffix + "@example.com", "Str0ng-pass", "Ana")).id());
    }

    @Test
    void adminsManageRegionsAndOnlyAdminsMayDoIt() throws Exception {
        Number id = JsonPath.read(saveRegion(post("/api/admin/delivery-regions"), "Zona Sul", "12.50", true,
                        "Copacabana", "Ipanema", "  ipanema ", "Leblon")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fee").value(12.5))
                .andExpect(jsonPath("$.districts.length()").value(3))
                .andExpect(jsonPath("$.districts[0]").value("Copacabana"))
                .andExpect(jsonPath("$.updatedBy").isNumber())
                .andReturn().getResponse().getContentAsString(), "$.id");

        saveRegion(put("/api/admin/delivery-regions/{id}", id), "Zona Sul", "18.00", true, "Copacabana", "Botafogo")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fee").value(18.0))
                .andExpect(jsonPath("$.districts.length()").value(2));

        mockMvc.perform(get("/api/admin/delivery-regions").with(admin))
                .andExpect(jsonPath("$[?(@.name == 'Zona Sul')].districts[1]").value("Copacabana"));
        mockMvc.perform(get("/api/admin/delivery-regions").with(ana)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/delivery-regions")).andExpect(status().isUnauthorized());

        mockMvc.perform(delete("/api/admin/delivery-regions/{id}", id).with(admin)).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/admin/delivery-regions/{id}", id).with(admin)).andExpect(status().isNotFound());
    }

    @Test
    void aDistrictBelongsToOneRegionAndNamesAreUnique() throws Exception {
        saveRegion(post("/api/admin/delivery-regions"), "Zona Norte", "20.00", true, "Tijuca", "Méier")
                .andExpect(status().isCreated());

        saveRegion(post("/api/admin/delivery-regions"), "Grande Tijuca", "22.00", true, "tijuca", "Vila Isabel")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.districts[0].district").value("Tijuca"))
                .andExpect(jsonPath("$.districts[0].region").value("Zona Norte"));
        saveRegion(post("/api/admin/delivery-regions"), "Outra", "22.00", true, "MEIER")
                .andExpect(status().isConflict());
        saveRegion(post("/api/admin/delivery-regions"), "zona norte", "22.00", true, "Grajaú")
                .andExpect(status().isConflict());
        saveRegion(post("/api/admin/delivery-regions"), "Sem bairro", "22.00", true)
                .andExpect(status().isBadRequest());
        saveRegion(post("/api/admin/delivery-regions"), "Negativa", "-1.00", true, "Grajaú")
                .andExpect(status().isBadRequest());
    }

    @Test
    void addressDistrictPicksTheFeeAndInactiveRegionsServeNobody() throws Exception {
        Number regionId = JsonPath.read(saveRegion(post("/api/admin/delivery-regions"), "Zona Sul", "12.50", true,
                        "Copacabana").andReturn().getResponse().getContentAsString(), "$.id");

        Number addressId = JsonPath.read(mockMvc.perform(post("/api/me/addresses").with(ana)
                        .contentType(MediaType.APPLICATION_JSON).content(ADDRESS.formatted("22041-001")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.delivery.region").value("Zona Sul"))
                .andExpect(jsonPath("$.delivery.fee").value(12.5))
                .andReturn().getResponse().getContentAsString(), "$.id");

        ProductResponse product = productService.create(
                new ProductRequest("REG-" + suffix, "Filtro", null, null, new BigDecimal("30.00"), 5, true));
        mockMvc.perform(put("/api/cart/items/{id}", product.id()).with(ana)
                .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":1}")).andExpect(status().isOk());

        saveRegion(put("/api/admin/delivery-regions/{id}", regionId), "Zona Sul", "12.50", false, "Copacabana")
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/me/addresses").with(ana)).andExpect(jsonPath("$[0].delivery").doesNotExist());
        checkout(addressId).andExpect(status().isUnprocessableContent());

        saveRegion(put("/api/admin/delivery-regions/{id}", regionId), "Zona Sul", "14.00", true, "Copacabana");
        checkout(addressId)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.deliveryFee").value(14.0))
                .andExpect(jsonPath("$.deliveryRegion").value("Zona Sul"))
                .andExpect(jsonPath("$.total").value(44.0));
    }

    @Test
    void uncoveredDistrictsListWhereCustomersLiveWithoutService() throws Exception {
        saveRegion(post("/api/admin/delivery-regions"), "Zona Sul", "12.50", true, "Copacabana");
        for (String cep : new String[] {"22041-001", "23050-000", "23050000"}) {
            mockMvc.perform(post("/api/me/addresses").with(ana)
                            .contentType(MediaType.APPLICATION_JSON).content(ADDRESS.formatted(cep)))
                    .andExpect(status().isCreated());
        }

        mockMvc.perform(get("/api/admin/delivery-regions/uncovered-districts").with(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.district == 'Campo Grande')].addresses").value(2))
                .andExpect(jsonPath("$[?(@.district == 'Copacabana')]").isEmpty());
    }

    private ResultActions saveRegion(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,
            String name, String fee, boolean active, String... districts) throws Exception {
        StringBuilder list = new StringBuilder();
        for (String district : districts) {
            list.append(list.isEmpty() ? "" : ",").append('"').append(district).append('"');
        }
        return mockMvc.perform(request.with(admin).contentType(MediaType.APPLICATION_JSON).content(
                "{\"name\":\"" + name + "\",\"fee\":" + fee + ",\"active\":" + active
                        + ",\"districts\":[" + list + "]}"));
    }

    private ResultActions checkout(Number addressId) throws Exception {
        return mockMvc.perform(post("/api/checkout").with(ana).header("Idempotency-Key", "key-" + UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON).content("{\"addressId\":" + addressId + "}"));
    }
}
