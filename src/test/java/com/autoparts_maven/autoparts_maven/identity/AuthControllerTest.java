package com.autoparts_maven.autoparts_maven.identity;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/** End-to-end JWT flow with real signed tokens (no mocked authentication). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AuthControllerTest {

    private static final String PASSWORD = "Str0ng-pass";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void registerLoginAndReadOwnProfile() throws Exception {
        register("Ana@Example.com", PASSWORD)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("ana@example.com"))
                .andExpect(jsonPath("$.role").value("CUSTOMER"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        String accessToken = JsonPath.read(login("ana@example.com", PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andReturn().getResponse().getContentAsString(), "$.accessToken");

        mockMvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("ana@example.com"))
                .andExpect(jsonPath("$.fullName").value("Test User"));
    }

    @Test
    void registerRejectsDuplicateEmailIgnoringCase() throws Exception {
        register("dup@example.com", PASSWORD).andExpect(status().isCreated());

        register("DUP@Example.com", PASSWORD)
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void registerValidatesEmailAndPassword() throws Exception {
        register("not-an-email", PASSWORD).andExpect(status().isBadRequest());
        register("short@example.com", "short").andExpect(status().isBadRequest());
    }

    @Test
    void wrongPasswordAndUnknownEmailGiveTheSameError() throws Exception {
        register("bob@example.com", PASSWORD).andExpect(status().isCreated());

        String wrongPassword = login("bob@example.com", "Wrong-pass1")
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();
        String unknownEmail = login("nobody@example.com", "Wrong-pass1")
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        org.assertj.core.api.Assertions.assertThat(unknownEmail).isEqualTo(wrongPassword);
    }

    @Test
    void refreshRotatesTokensAndReuseRevokesEverySession() throws Exception {
        register("carol@example.com", PASSWORD).andExpect(status().isCreated());
        String first = refreshTokenOf(login("carol@example.com", PASSWORD));

        String second = refreshTokenOf(refresh(first).andExpect(status().isOk()));

        refresh(first).andExpect(status().isUnauthorized());
        refresh(second).andExpect(status().isUnauthorized());
    }

    @Test
    void logoutRevokesTheRefreshToken() throws Exception {
        register("dave@example.com", PASSWORD).andExpect(status().isCreated());
        String refreshToken = refreshTokenOf(login("dave@example.com", PASSWORD));

        mockMvc.perform(post("/api/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
                .andExpect(status().isNoContent());

        refresh(refreshToken).andExpect(status().isUnauthorized());
    }

    @Test
    void customerTokenCannotUseAdminEndpoints() throws Exception {
        register("eve@example.com", PASSWORD).andExpect(status().isCreated());
        String token = accessTokenOf(login("eve@example.com", PASSWORD));

        mockMvc.perform(get("/api/admin/users").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/products/1/vehicles/1").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void bootstrappedAdminCanLogInAndUseAdminEndpoints() throws Exception {
        String token = accessTokenOf(login("admin@autoparts.local", "admin").andExpect(status().isOk()));

        mockMvc.perform(get("/api/admin/users").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].email").exists());
    }

    @Test
    void invalidOrMissingTokenIsRejectedWithProblemDetail() throws Exception {
        mockMvc.perform(get("/api/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, containsString("Bearer")));

        mockMvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void changingPasswordInvalidatesOldPasswordAndSessions() throws Exception {
        register("frank@example.com", PASSWORD).andExpect(status().isCreated());
        ResultActions session = login("frank@example.com", PASSWORD);
        String token = accessTokenOf(session);
        String refreshToken = refreshTokenOf(session);

        mockMvc.perform(put("/api/me/password")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"Wrong-pass1\",\"newPassword\":\"N3w-password\"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(put("/api/me/password")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"" + PASSWORD + "\",\"newPassword\":\"N3w-password\"}"))
                .andExpect(status().isNoContent());

        login("frank@example.com", PASSWORD).andExpect(status().isUnauthorized());
        login("frank@example.com", "N3w-password").andExpect(status().isOk());
        refresh(refreshToken).andExpect(status().isUnauthorized());
    }

    private ResultActions register(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\",\"fullName\":\"Test User\"}"));
    }

    private ResultActions login(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"));
    }

    private ResultActions refresh(String refreshToken) throws Exception {
        return mockMvc.perform(post("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + refreshToken + "\"}"));
    }

    private static String accessTokenOf(ResultActions result) throws Exception {
        return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.accessToken");
    }

    private static String refreshTokenOf(ResultActions result) throws Exception {
        return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.refreshToken");
    }
}
