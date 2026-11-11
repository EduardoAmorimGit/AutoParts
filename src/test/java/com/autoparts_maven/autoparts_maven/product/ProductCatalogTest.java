package com.autoparts_maven.autoparts_maven.product;

import static com.autoparts_maven.autoparts_maven.support.TestAuth.admin;
import static com.autoparts_maven.autoparts_maven.support.TestAuth.customer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.autoparts_maven.autoparts_maven.product.dto.ProductRequest;
import com.autoparts_maven.autoparts_maven.product.dto.ProductResponse;
import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ProductCatalogTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProductService productService;

    @Autowired
    private ProductRepository productRepository;

    @Test
    void customersNeverSeeInactiveProductsButAdminsCan() throws Exception {
        ProductResponse inactive = productService.create(
                new ProductRequest("CAT-INACTIVE", "Hidden part", null, null, new BigDecimal("1.00"), 1, false));

        mockMvc.perform(get("/api/products").param("q", "CAT-INACTIVE"))
                .andExpect(jsonPath("$.totalElements").value(0));
        mockMvc.perform(get("/api/products").param("q", "CAT-INACTIVE").param("includeInactive", "true").with(customer()))
                .andExpect(jsonPath("$.totalElements").value(0));
        mockMvc.perform(get("/api/products/{id}", inactive.id()))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/products").param("q", "CAT-INACTIVE").param("includeInactive", "true").with(admin()))
                .andExpect(jsonPath("$.totalElements").value(1));
        mockMvc.perform(get("/api/products/{id}", inactive.id()).with(admin()))
                .andExpect(status().isOk());
    }

    @Test
    void createsProductWithCatalogAttributesAndFiltersByTypeCategoryAndText() throws Exception {
        Number categoryId = JsonPath.read(mockMvc.perform(post("/api/admin/categories").with(admin())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Óleos de Motor\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.slug").value("oleos-de-motor"))
                .andReturn().getResponse().getContentAsString(), "$.id");

        mockMvc.perform(post("/api/products").with(admin()).contentType(MediaType.APPLICATION_JSON).content("""
                        {"sku":"cat-oil-1","name":"Oleo Sintetico Catalogo","price":45.90,"stockQuantity":12,
                         "type":"ENGINE_OIL","categoryId":%s,"viscosity":"5w-30","oilSpecification":"API SP",
                         "packageVolumeLiters":1,"imageUrls":["https://cdn.example.com/oil.jpg"]}
                        """.formatted(categoryId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("ENGINE_OIL"))
                .andExpect(jsonPath("$.category.slug").value("oleos-de-motor"))
                .andExpect(jsonPath("$.viscosity").value("5W30"))
                .andExpect(jsonPath("$.imageUrls[0]").value("https://cdn.example.com/oil.jpg"));
        productService.create(new ProductRequest("CAT-FLT-1", "Filtro Catalogo", null, null, new BigDecimal("20.00"), 3, true));

        mockMvc.perform(get("/api/products").param("type", "ENGINE_OIL").param("q", "catalogo"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].sku").value("CAT-OIL-1"));
        mockMvc.perform(get("/api/products").param("categoryId", String.valueOf(categoryId)))
                .andExpect(jsonPath("$.totalElements").value(1));
        mockMvc.perform(get("/api/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.slug == 'oleos-de-motor')].name").value("Óleos de Motor"));
    }

    @Test
    void rejectsNonHttpsImagesAndDuplicateCategories() throws Exception {
        mockMvc.perform(post("/api/products").with(admin()).contentType(MediaType.APPLICATION_JSON).content("""
                        {"sku":"cat-img","name":"Img","price":1,"stockQuantity":1,"imageUrls":["http://x.com/a.jpg"]}
                        """))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/admin/categories").with(admin())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Filtros\"}"))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/admin/categories").with(admin())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"filtros\"}"))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/api/admin/categories").with(customer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"X\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void deletingACategoryKeepsItsProducts() throws Exception {
        Number categoryId = JsonPath.read(mockMvc.perform(post("/api/admin/categories").with(admin())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Temporaria\"}"))
                .andReturn().getResponse().getContentAsString(), "$.id");
        ProductResponse product = productService.create(new ProductRequest("CAT-TMP", "Temp", null, null,
                new BigDecimal("1.00"), 1, true, null, categoryId.longValue(), null, null, null, null));

        mockMvc.perform(delete("/api/admin/categories/{id}", categoryId).with(admin()))
                .andExpect(status().isNoContent());
        productRepository.flush();

        mockMvc.perform(get("/api/products/{id}", product.id()))
                .andExpect(status().isOk());
    }

    @Test
    void importsProductsCreatingUpdatingAndSkippingUnpricedRows() throws Exception {
        productService.create(new ProductRequest("IMP-EXISTING", "Old name", null, "Wega", new BigDecimal("10.00"), 1, true));

        importCsv("""
                sku;nome_sugerido;tipo;categoria;preco;estoque;viscosidade;volume_litros
                IMP-NEW-1;Filtro de oleo Wega IMP;Filtro de oleo;Filtros de Oleo;29,90;8;;
                imp-existing;;Filtro de ar;;12,50;;;
                IMP-NOPRICE;Filtro sem preco;Filtro de ar;;;;;
                IMP-BADTYPE;Item;Oleo;;10;1;;
                IMP-OIL-1;Oleo 5W30 1L;Oleo do motor;Oleos;R$ 39,90;20;5w30;1
                """, false)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRows").value(5))
                .andExpect(jsonPath("$.created").value(2))
                .andExpect(jsonPath("$.updated").value(1))
                .andExpect(jsonPath("$.skippedWithoutPrice").value(1))
                .andExpect(jsonPath("$.errors.length()").value(1))
                .andExpect(jsonPath("$.errors[0].line").value(5));

        Product updated = productRepository.findBySku("IMP-EXISTING").orElseThrow();
        org.assertj.core.api.Assertions.assertThat(updated.getName()).isEqualTo("Old name");
        org.assertj.core.api.Assertions.assertThat(updated.getPrice()).isEqualByComparingTo("12.50");
        org.assertj.core.api.Assertions.assertThat(updated.getType()).isEqualTo(ProductType.AIR_FILTER);

        Product oil = productRepository.findBySku("IMP-OIL-1").orElseThrow();
        org.assertj.core.api.Assertions.assertThat(oil.getType()).isEqualTo(ProductType.ENGINE_OIL);
        org.assertj.core.api.Assertions.assertThat(oil.getViscosity()).isEqualTo("5W30");
        org.assertj.core.api.Assertions.assertThat(oil.getPrice()).isEqualByComparingTo("39.90");
        org.assertj.core.api.Assertions.assertThat(oil.getCategory().getSlug()).isEqualTo("oleos");
    }

    @Test
    void invalidRowNeverPartiallyChangesAnExistingProduct() throws Exception {
        productService.create(new ProductRequest("IMP-SAFE", "Original", null, null, new BigDecimal("10.00"), 1, true));

        importCsv("sku;nome;preco;tipo\nIMP-SAFE;Changed name;99,00;Oleo\n", false)
                .andExpect(jsonPath("$.errors.length()").value(1));
        productRepository.flush();

        Product product = productRepository.findBySku("IMP-SAFE").orElseThrow();
        org.assertj.core.api.Assertions.assertThat(product.getName()).isEqualTo("Original");
        org.assertj.core.api.Assertions.assertThat(product.getPrice()).isEqualByComparingTo("10.00");
    }

    @Test
    void importRejectsMissingColumns() throws Exception {
        importCsv("sku;nome\nA;B\n", false).andExpect(status().isBadRequest());
    }

    // Outside the test transaction so the dry run's own rollback is observable.
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void dryRunSavesNothing() throws Exception {
        importCsv("sku;nome;preco;categoria\nIMP-DRY-1;Dry;10;Categoria Dry Run\n", true)
                .andExpect(jsonPath("$.dryRun").value(true))
                .andExpect(jsonPath("$.created").value(1));

        org.assertj.core.api.Assertions.assertThat(productRepository.findBySku("IMP-DRY-1")).isEmpty();
        mockMvc.perform(get("/api/categories"))
                .andExpect(jsonPath("$[?(@.slug == 'categoria-dry-run')]").isEmpty());
    }

    private ResultActions importCsv(String csv, boolean dryRun) throws Exception {
        return mockMvc.perform(multipart("/api/admin/products/import")
                .file(new MockMultipartFile("file", "produtos.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8)))
                .param("dryRun", String.valueOf(dryRun))
                .with(admin()));
    }
}
