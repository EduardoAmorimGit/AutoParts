package com.autoparts_maven.autoparts_maven.product;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.autoparts_maven.autoparts_maven.product.dto.ProductRequest;
import com.autoparts_maven.autoparts_maven.product.dto.ProductResponse;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ProductControllerTest {

    private static final String ADMIN_USER = "admin";
    private static final String ADMIN_PASSWORD = "admin";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProductService productService;

    @Test
    void listsProductsPublicly() throws Exception {
        mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
    }

    @Test
    void findsProductByIdPublicly() throws Exception {
        ProductResponse product = seed("SKU-100", "Oil Filter");

        mockMvc.perform(get("/api/products/{id}", product.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(product.id()))
                .andExpect(jsonPath("$.sku").value("SKU-100"))
                .andExpect(jsonPath("$.name").value("Oil Filter"));
    }

    @Test
    void returnsNotFoundForUnknownId() throws Exception {
        mockMvc.perform(get("/api/products/{id}", 999_999))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectsWriteWithoutCredentials() throws Exception {
        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("SKU-200", "Air Filter", "25.00", 5)))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Basic realm=\"AutoParts\""))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    @WithMockUser(username = "viewer", roles = "VIEWER")
    void rejectsWriteForAuthenticatedUserWithoutAdminRole() throws Exception {
        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("SKU-202", "Cabin Filter", "30.00", 4)))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void createsProductWhenAuthenticated() throws Exception {
        mockMvc.perform(post("/api/products")
                        .with(httpBasic(ADMIN_USER, ADMIN_PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("SKU-201", "Spark Plug", "12.50", 8)))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.sku").value("SKU-201"))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void rejectsDuplicateSku() throws Exception {
        seed("SKU-300", "Brake Pad");

        mockMvc.perform(post("/api/products")
                        .with(httpBasic(ADMIN_USER, ADMIN_PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("SKU-300", "Other", "10.00", 1)))
                .andExpect(status().isConflict());
    }

    @Test
    void rejectsInvalidPayload() throws Exception {
        mockMvc.perform(post("/api/products")
                        .with(httpBasic(ADMIN_USER, ADMIN_PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("", "", "-1.00", -1)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors").exists());
    }

    @Test
    void updatesProductWhenAuthenticated() throws Exception {
        ProductResponse product = seed("SKU-400", "Fuel Pump");

        mockMvc.perform(put("/api/products/{id}", product.id())
                        .with(httpBasic(ADMIN_USER, ADMIN_PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("SKU-400", "Fuel Pump Premium", "199.90", 3)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Fuel Pump Premium"));
    }

    @Test
    void returnsNotFoundWhenUpdatingUnknownId() throws Exception {
        mockMvc.perform(put("/api/products/{id}", 999_999)
                        .with(httpBasic(ADMIN_USER, ADMIN_PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("SKU-500", "Radiator", "300.00", 2)))
                .andExpect(status().isNotFound());
    }

    @Test
    void deletesProductWhenAuthenticated() throws Exception {
        ProductResponse product = seed("SKU-600", "Shock Absorber");

        mockMvc.perform(delete("/api/products/{id}", product.id())
                        .with(httpBasic(ADMIN_USER, ADMIN_PASSWORD)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/products/{id}", product.id()))
                .andExpect(status().isNotFound());
    }

    @Test
    void returnsPageStructureForListing() throws Exception {
        mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20));
    }

    @Test
    void paginatesProducts() throws Exception {
        seed("SKU-P-1", "P One");
        seed("SKU-P-2", "P Two");
        ProductResponse third = seed("SKU-P-3", "P Three");

        mockMvc.perform(get("/api/products")
                        .param("page", "0")
                        .param("size", "2")
                        .param("sort", "id,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.first").value(true))
                .andExpect(jsonPath("$.last").value(false));

        mockMvc.perform(get("/api/products")
                        .param("page", "1")
                        .param("size", "2")
                        .param("sort", "id,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(third.id()))
                .andExpect(jsonPath("$.last").value(true));
    }

    @Test
    void sortsProductsByRequestedDirection() throws Exception {
        seed("SKU-S-1", "S One");
        seed("SKU-S-2", "S Two");
        ProductResponse last = seed("SKU-S-3", "S Three");

        mockMvc.perform(get("/api/products").param("size", "1").param("sort", "id,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(last.id()));
    }

    @Test
    void rejectsInvalidSortProperty() throws Exception {
        mockMvc.perform(get("/api/products").param("sort", "bogus"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void returnsBadRequestForNonNumericId() throws Exception {
        mockMvc.perform(get("/api/products/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void returnsNotFoundForUnknownSubPath() throws Exception {
        mockMvc.perform(get("/api/products/1/compat"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void rejectsMalformedJson() throws Exception {
        mockMvc.perform(post("/api/products")
                        .with(httpBasic(ADMIN_USER, ADMIN_PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void rejectsUnsupportedMediaType() throws Exception {
        mockMvc.perform(post("/api/products")
                        .with(httpBasic(ADMIN_USER, ADMIN_PASSWORD))
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("not json"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(415));
    }

    @Test
    void rejectsUnsupportedMethod() throws Exception {
        mockMvc.perform(patch("/api/products/1")
                        .with(httpBasic(ADMIN_USER, ADMIN_PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(405));
    }

    @Test
    void rejectsTooLongBrand() throws Exception {
        String brand = "b".repeat(101);

        mockMvc.perform(post("/api/products")
                        .with(httpBasic(ADMIN_USER, ADMIN_PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"SKU-BRAND\",\"name\":\"Brand Test\",\"brand\":\"" + brand
                                + "\",\"price\":1.00,\"stockQuantity\":1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.brand").exists());
    }

    @Test
    void normalizesSkuOnCreate() throws Exception {
        mockMvc.perform(post("/api/products")
                        .with(httpBasic(ADMIN_USER, ADMIN_PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("  sku-lower  ", "Normalized", "10.00", 1)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sku").value("SKU-LOWER"));
    }

    @Test
    void rejectsDuplicateSkuIgnoringCaseAndSpaces() throws Exception {
        seed("SKU-DUP-1", "Dup");

        mockMvc.perform(post("/api/products")
                        .with(httpBasic(ADMIN_USER, ADMIN_PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("  sku-dup-1 ", "Other", "10.00", 1)))
                .andExpect(status().isConflict());
    }

    private ProductResponse seed(String sku, String name) {
        return productService.create(
                new ProductRequest(sku, name, "desc", "brand", new BigDecimal("99.90"), 10, true));
    }

    private String body(String sku, String name, String price, int stockQuantity) {
        return """
                {"sku":"%s","name":"%s","price":%s,"stockQuantity":%d}
                """.formatted(sku, name, price, stockQuantity);
    }
}
