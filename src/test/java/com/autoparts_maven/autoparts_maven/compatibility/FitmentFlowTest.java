package com.autoparts_maven.autoparts_maven.compatibility;

import static com.autoparts_maven.autoparts_maven.support.TestAuth.customer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.autoparts_maven.autoparts_maven.product.ProductService;
import com.autoparts_maven.autoparts_maven.product.dto.ProductRequest;
import com.autoparts_maven.autoparts_maven.product.dto.ProductResponse;
import com.autoparts_maven.autoparts_maven.vehicle.VehicleLookupTestConfig;
import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * End-to-end compatibility: catalog import -> review -> plate lookup -> spec -> products, plus
 * the pending-vehicle queue and manual resolution. Uses a real admin JWT from the login endpoint.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(VehicleLookupTestConfig.class)
@Transactional
class FitmentFlowTest {

    private static final String HEADER =
            "sku;marca;modelo;versao;motor;combustivel;ano_inicio;ano_fim;capacidade_oleo_litros;fonte;observacao\n";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProductService productService;

    private String adminToken;

    @BeforeEach
    void loginAsBootstrappedAdmin() throws Exception {
        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"admin@autoparts.local\",\"password\":\"admin\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        adminToken = JsonPath.read(body, "$.accessToken");
    }

    @Test
    void importReportsCreatedExistingAndInvalidRows() throws Exception {
        product("FF-OIL-1", "Filtro de oleo", true);
        product("FF-5W30", "Oleo 5W30", true);

        importCsv(HEADER
                + "FF-OIL-1;VW;Gol;;1.0;Flex;2017;2022;4,0;Catalogo Tecfil 2025 p.34;\n"
                + "ff-oil-1;Volkswagen;GOL;;1,0;FLEX;2017;2022;4;Catalogo Tecfil 2025 p.34;\n"
                + "NOPE-1;VW;Gol;;1.0;Flex;2017;2022;;Catalogo;\n"
                + "FF-OIL-1;VW;Gol;;1.0;Flex;2022;2017;;Catalogo;\n"
                + "FF-5W30;VW;Gol;;1.0;Flex;2017;2022;;Catalogo Lubrax;\n", false)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRows").value(5))
                .andExpect(jsonPath("$.specsCreated").value(1))
                .andExpect(jsonPath("$.fitmentsCreated").value(2))
                .andExpect(jsonPath("$.fitmentsAlreadyPresent").value(1))
                .andExpect(jsonPath("$.errors.length()").value(2))
                .andExpect(jsonPath("$.errors[0].line").value(4))
                .andExpect(jsonPath("$.errors[1].line").value(5));

        mockMvc.perform(get("/api/admin/fitments").param("status", "IMPORTED").header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].source").value("Catalogo Tecfil 2025 p.34"))
                .andExpect(jsonPath("$.content[0].spec.oilCapacityLiters").value(4));
    }

    // Runs outside the test transaction: a dry run rolls back its own request transaction, which
    // can only be observed when the import is not nested in an outer (test) transaction.
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void dryRunSavesNothing() throws Exception {
        ProductResponse product = product("FF-DRY-1", "Filtro", true);
        try {
            importCsv(HEADER + "FF-DRY-1;VW;Fusca Teste;;1.0;Flex;2017;2022;;Catalogo;\n", true)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.dryRun").value(true))
                    .andExpect(jsonPath("$.fitmentsCreated").value(1))
                    .andExpect(jsonPath("$.specsCreated").value(1));

            mockMvc.perform(get("/api/admin/fitments").param("productId", String.valueOf(product.id()))
                            .header(HttpHeaders.AUTHORIZATION, bearer()))
                    .andExpect(jsonPath("$.totalElements").value(0));
            mockMvc.perform(get("/api/admin/vehicle-specs").param("model", "Fusca Teste")
                            .header(HttpHeaders.AUTHORIZATION, bearer()))
                    .andExpect(jsonPath("$.totalElements").value(0));
        } finally {
            productService.delete(product.id());
        }
    }

    @Test
    void importRejectsFileWithoutRequiredColumns() throws Exception {
        importCsv("sku;marca\nA;VW\n", false)
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void customerSeesOnlyValidatedActiveProductsOfTheResolvedSpec() throws Exception {
        ProductResponse filter = product("FF-CUS-1", "Filtro de oleo", true);
        product("FF-CUS-2", "Filtro inativo", false);
        importCsv(HEADER
                + "FF-CUS-1;VW;Gol;;1.0;Flex;2017;2022;4;Catalogo Tecfil;\n"
                + "FF-CUS-2;VW;Gol;;1.0;Flex;2017;2022;4;Catalogo Tecfil;\n", false)
                .andExpect(jsonPath("$.fitmentsCreated").value(2));
        Long vehicleId = lookup(VehicleLookupTestConfig.KNOWN_PLATE);

        // Imported but not reviewed: the vehicle resolves, but nothing is shown yet.
        mockMvc.perform(get("/api/vehicles/{id}/products", vehicleId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"))
                .andExpect(jsonPath("$.spec.model").value("GOL"))
                .andExpect(jsonPath("$.spec.oilCapacityLiters").value(4))
                .andExpect(jsonPath("$.products.totalElements").value(0));

        validateAll();

        mockMvc.perform(get("/api/vehicles/{id}/products", vehicleId))
                .andExpect(jsonPath("$.products.totalElements").value(1))
                .andExpect(jsonPath("$.products.content[0].id").value(filter.id()));
    }

    @Test
    void unknownModelIsPendingUntilAnAdminAssignsASpecWhichThenCoversIdenticalVehicles() throws Exception {
        Long first = lookup(VehicleLookupTestConfig.TWIN_PLATE_A);
        mockMvc.perform(get("/api/vehicles/{id}/products", first))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.products.totalElements").value(0));

        mockMvc.perform(get("/api/admin/vehicles/pending").header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.id == " + first + ")].model").value("CHEV/ONIX 10MT JOYE"));

        String spec = mockMvc.perform(post("/api/admin/vehicle-specs").header(HttpHeaders.AUTHORIZATION, bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"brand\":\"Chevrolet\",\"model\":\"Onix Joy\",\"engine\":\"1.0\",\"fuel\":\"Flex\","
                                + "\"yearStart\":2017,\"yearEnd\":2020,\"oilCapacityLiters\":3.5}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.brand").value("CHEVROLET"))
                .andReturn().getResponse().getContentAsString();
        Number specId = JsonPath.read(spec, "$.id");

        mockMvc.perform(put("/api/admin/vehicles/{id}/spec", first).header(HttpHeaders.AUTHORIZATION, bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"specId\":" + specId + "}"))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/vehicles/{id}/products", first))
                .andExpect(jsonPath("$.status").value("RESOLVED"))
                .andExpect(jsonPath("$.spec.id").value(specId));

        Long twin = lookup(VehicleLookupTestConfig.TWIN_PLATE_B);
        mockMvc.perform(get("/api/vehicles/{id}/products", twin))
                .andExpect(jsonPath("$.status").value("RESOLVED"))
                .andExpect(jsonPath("$.spec.id").value(specId));
    }

    @Test
    void duplicateSpecIsRejected() throws Exception {
        String body = "{\"brand\":\"VW\",\"model\":\"Fox\",\"yearStart\":2010,\"yearEnd\":2015}";
        mockMvc.perform(post("/api/admin/vehicle-specs").header(HttpHeaders.AUTHORIZATION, bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/admin/vehicle-specs").header(HttpHeaders.AUTHORIZATION, bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(body.replace("VW", "Volkswagen")))
                .andExpect(status().isConflict());
    }

    @Test
    void adminEndpointsRequireTheAdminRole() throws Exception {
        mockMvc.perform(get("/api/admin/fitments")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/admin/fitments").with(customer())).andExpect(status().isForbidden());
        mockMvc.perform(multipart("/api/admin/fitments/import")
                        .file(new MockMultipartFile("file", "f.csv", "text/csv", "x".getBytes()))
                        .with(customer()))
                .andExpect(status().isForbidden());
    }

    private void validateAll() throws Exception {
        String page = mockMvc.perform(get("/api/admin/fitments").param("status", "IMPORTED").param("size", "100")
                        .header(HttpHeaders.AUTHORIZATION, bearer()))
                .andReturn().getResponse().getContentAsString();
        List<Number> ids = JsonPath.read(page, "$.content[*].id");
        mockMvc.perform(post("/api/admin/fitments/review").header(HttpHeaders.AUTHORIZATION, bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ids\":" + ids + ",\"decision\":\"VALIDATED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updated").value(ids.size()));
    }

    private ResultActions importCsv(String csv, boolean dryRun) throws Exception {
        return mockMvc.perform(multipart("/api/admin/fitments/import")
                .file(new MockMultipartFile("file", "catalogo.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8)))
                .param("dryRun", String.valueOf(dryRun))
                .header(HttpHeaders.AUTHORIZATION, bearer()));
    }

    private Long lookup(String plate) throws Exception {
        String body = mockMvc.perform(post("/api/vehicles/lookup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"plate\":\"" + plate + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Number id = JsonPath.read(body, "$.id");
        return id.longValue();
    }

    private ProductResponse product(String sku, String name, boolean active) {
        return productService.create(new ProductRequest(sku, name, null, null, new BigDecimal("30.00"), 5, active));
    }

    private String bearer() {
        return "Bearer " + adminToken;
    }
}
