package com.autoparts_maven.autoparts_maven.compatibility;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.autoparts_maven.autoparts_maven.product.ProductService;
import com.autoparts_maven.autoparts_maven.product.dto.ProductRequest;
import com.autoparts_maven.autoparts_maven.product.dto.ProductResponse;
import com.autoparts_maven.autoparts_maven.vehicle.Vehicle;
import com.autoparts_maven.autoparts_maven.vehicle.VehicleRepository;
import com.autoparts_maven.autoparts_maven.vehicle.VehicleSource;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class CompatibilityControllerTest {

    private static final String ADMIN_USER = "admin";
    private static final String ADMIN_PASSWORD = "admin";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProductService productService;

    @Autowired
    private VehicleRepository vehicleRepository;

    @Test
    void adminLinksProductAndCustomerSeesIt() throws Exception {
        ProductResponse product = seedProduct("SKU-CC-1");
        Vehicle vehicle = seedVehicle("CCA1B23");

        mockMvc.perform(put("/api/products/{p}/vehicles/{v}", product.id(), vehicle.getId())
                        .with(httpBasic(ADMIN_USER, ADMIN_PASSWORD)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/vehicles/{v}/products", vehicle.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(product.id()));
    }

    @Test
    void linkingIsIdempotent() throws Exception {
        ProductResponse product = seedProduct("SKU-CC-2");
        Vehicle vehicle = seedVehicle("CCB1C23");

        for (int i = 0; i < 2; i++) {
            mockMvc.perform(put("/api/products/{p}/vehicles/{v}", product.id(), vehicle.getId())
                            .with(httpBasic(ADMIN_USER, ADMIN_PASSWORD)))
                    .andExpect(status().isNoContent());
        }

        mockMvc.perform(get("/api/vehicles/{v}/products", vehicle.getId()))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void unlinkRemovesProductFromCustomerView() throws Exception {
        ProductResponse product = seedProduct("SKU-CC-3");
        Vehicle vehicle = seedVehicle("CCC1D23");

        mockMvc.perform(put("/api/products/{p}/vehicles/{v}", product.id(), vehicle.getId())
                        .with(httpBasic(ADMIN_USER, ADMIN_PASSWORD)))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/products/{p}/vehicles/{v}", product.id(), vehicle.getId())
                        .with(httpBasic(ADMIN_USER, ADMIN_PASSWORD)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/vehicles/{v}/products", vehicle.getId()))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void linkRequiresAuthentication() throws Exception {
        mockMvc.perform(put("/api/products/{p}/vehicles/{v}", 1, 1))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    @WithMockUser(username = "customer", roles = "CUSTOMER")
    void linkRequiresAdminRole() throws Exception {
        mockMvc.perform(put("/api/products/{p}/vehicles/{v}", 1, 1))
                .andExpect(status().isForbidden());
    }

    @Test
    void linkReturnsNotFoundForUnknownVehicle() throws Exception {
        ProductResponse product = seedProduct("SKU-CC-4");

        mockMvc.perform(put("/api/products/{p}/vehicles/{v}", product.id(), 999_999)
                        .with(httpBasic(ADMIN_USER, ADMIN_PASSWORD)))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void vehiclesOfAProductArePrivateBecauseTheyExposePlates() throws Exception {
        ProductResponse product = seedProduct("SKU-CC-5");

        mockMvc.perform(get("/api/products/{p}/vehicles", product.id()))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/products/{p}/vehicles", product.id())
                        .with(httpBasic(ADMIN_USER, ADMIN_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void productsOfAVehicleSortByProductFieldsAndRejectUnknownSort() throws Exception {
        ProductResponse zeta = seedProduct("SKU-CC-6", "Zeta");
        ProductResponse alpha = seedProduct("SKU-CC-7", "Alpha");
        Vehicle vehicle = seedVehicle("CCD1E23");
        for (ProductResponse product : new ProductResponse[] {zeta, alpha}) {
            mockMvc.perform(put("/api/products/{p}/vehicles/{v}", product.id(), vehicle.getId())
                            .with(httpBasic(ADMIN_USER, ADMIN_PASSWORD)))
                    .andExpect(status().isNoContent());
        }

        mockMvc.perform(get("/api/vehicles/{v}/products", vehicle.getId()).param("sort", "name,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].name").value("Alpha"))
                .andExpect(jsonPath("$.content[1].name").value("Zeta"));

        mockMvc.perform(get("/api/vehicles/{v}/products", vehicle.getId()).param("sort", "bogus"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void productsOfUnknownVehicleReturnNotFound() throws Exception {
        mockMvc.perform(get("/api/vehicles/{v}/products", 999_999))
                .andExpect(status().isNotFound());
    }

    private ProductResponse seedProduct(String sku) {
        return seedProduct(sku, "Part " + sku);
    }

    private ProductResponse seedProduct(String sku, String name) {
        return productService.create(new ProductRequest(sku, name, null, null, new BigDecimal("10.00"), 1, true));
    }

    private Vehicle seedVehicle(String plate) {
        Vehicle vehicle = new Vehicle();
        vehicle.setPlate(plate);
        vehicle.setBrand("FORD");
        vehicle.setModel("KA");
        vehicle.setManufactureYear(2019);
        vehicle.setSource(VehicleSource.SIMPLES_API);
        return vehicleRepository.save(vehicle);
    }
}
