package com.autoparts_maven.autoparts_maven.vehicle;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(VehicleLookupTestConfig.class)
@Transactional
class VehicleControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void lookupIsPublicAndReturnsVehicle() throws Exception {
        mockMvc.perform(post("/api/vehicles/lookup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"plate\":\"abc1d23\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plate").value(VehicleLookupTestConfig.KNOWN_PLATE))
                .andExpect(jsonPath("$.brand").value("VOLKSWAGEN"))
                .andExpect(jsonPath("$.source").value("SIMPLES_API"));
    }

    @Test
    void lookupRejectsInvalidPlate() throws Exception {
        mockMvc.perform(post("/api/vehicles/lookup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"plate\":\"nope\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void lookupReturnsNotFoundWhenVehicleIsUnknown() throws Exception {
        mockMvc.perform(post("/api/vehicles/lookup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"plate\":\"" + VehicleLookupTestConfig.UNKNOWN_PLATE + "\"}"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void lookupRejectsBlankPlate() throws Exception {
        mockMvc.perform(post("/api/vehicles/lookup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"plate\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void lookupNotFoundDoesNotEchoThePlate() throws Exception {
        mockMvc.perform(post("/api/vehicles/lookup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"plate\":\"" + VehicleLookupTestConfig.UNKNOWN_PLATE + "\"}"))
                .andExpect(status().isNotFound())
                .andExpect(content().string(not(containsString(VehicleLookupTestConfig.UNKNOWN_PLATE))));
    }

    @Test
    void findByIdRequiresAdminBecauseItExposesThePlate() throws Exception {
        Long id = lookupAndGetId();

        mockMvc.perform(get("/api/vehicles/{id}", id))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/vehicles/{id}", id).with(httpBasic("admin", "admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id));
    }

    @Test
    void findByIdReturnsNotFoundForUnknownId() throws Exception {
        mockMvc.perform(get("/api/vehicles/{id}", 999_999).with(httpBasic("admin", "admin")))
                .andExpect(status().isNotFound());
    }

    private Long lookupAndGetId() throws Exception {
        String body = mockMvc.perform(post("/api/vehicles/lookup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"plate\":\"" + VehicleLookupTestConfig.KNOWN_PLATE + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Number id = com.jayway.jsonpath.JsonPath.read(body, "$.id");
        return id.longValue();
    }
}
