package com.autoparts_maven.autoparts_maven.identity;

import static com.autoparts_maven.autoparts_maven.support.TestAuth.admin;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.autoparts_maven.autoparts_maven.notification.EmailOutbox;
import com.autoparts_maven.autoparts_maven.notification.EmailOutboxRepository;
import com.autoparts_maven.autoparts_maven.notification.EmailOutboxService;
import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Instant;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "auth.password-reset.url=https://loja.example/redefinir-senha")
@Transactional
class PasswordResetFlowTest {

    private static final String PASSWORD = "Str0ng-pass";
    private static final String NEW_PASSWORD = "N3w-password";
    private static final Pattern TOKEN = Pattern.compile("https://loja\\.example/redefinir-senha#token=([A-Za-z0-9_-]+)");

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private EmailOutboxRepository outbox;
    @Autowired
    private EmailOutboxService outboxService;
    @Autowired
    private JdbcTemplate jdbc;
    @PersistenceContext
    private EntityManager entityManager;

    @Test
    void unknownEmailIsAcceptedButSendsNothing() throws Exception {
        forgot("nobody@example.com").andExpect(status().isAccepted());

        assertThat(emails("PASSWORD_RESET")).isEmpty();
    }

    @Test
    void resetLinkSetsANewPasswordOnceAndSignsOutEverywhere() throws Exception {
        register("reset@example.com");
        String refreshToken = JsonPath.read(login("reset@example.com", PASSWORD)
                .andReturn().getResponse().getContentAsString(), "$.refreshToken");

        forgot("  RESET@Example.com ").andExpect(status().isAccepted());
        EmailOutbox email = emails("PASSWORD_RESET").get(0);
        assertThat(email.isSensitive()).isTrue();
        assertThat(email.getToAddress()).isEqualTo("reset@example.com");
        String token = tokenIn(email.getBody());

        reset(token, NEW_PASSWORD).andExpect(status().isNoContent());

        login("reset@example.com", PASSWORD).andExpect(status().isUnauthorized());
        login("reset@example.com", NEW_PASSWORD).andExpect(status().isOk());
        refresh(refreshToken).andExpect(status().isUnauthorized());
        reset(token, "An0ther-pass").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid password reset link"));
        assertThat(emails("PASSWORD_CHANGED")).hasSize(1);
    }

    @Test
    void unknownAndExpiredLinksAreRejected() throws Exception {
        register("expired@example.com");
        reset("not-a-real-token", NEW_PASSWORD).andExpect(status().isBadRequest());

        forgot("expired@example.com");
        String token = tokenIn(emails("PASSWORD_RESET").get(0).getBody());
        jdbc.update("UPDATE password_reset_tokens SET expires_at = ?", java.sql.Timestamp.from(Instant.now().minusSeconds(1)));
        entityManager.clear();

        reset(token, NEW_PASSWORD).andExpect(status().isBadRequest());
        login("expired@example.com", PASSWORD).andExpect(status().isOk());
    }

    @Test
    void secondRequestWithinTheCooldownSendsNoNewLink() throws Exception {
        register("cooldown@example.com");

        forgot("cooldown@example.com").andExpect(status().isAccepted());
        forgot("cooldown@example.com").andExpect(status().isAccepted());

        assertThat(emails("PASSWORD_RESET")).hasSize(1);
    }

    @Test
    void aNewerLinkVoidsTheOlderOne() throws Exception {
        register("newer@example.com");
        forgot("newer@example.com");
        String older = tokenIn(emails("PASSWORD_RESET").get(0).getBody());
        jdbc.update("UPDATE password_reset_tokens SET created_at = created_at - INTERVAL '10 minutes'");

        forgot("newer@example.com");
        List<EmailOutbox> sent = emails("PASSWORD_RESET");
        assertThat(sent).hasSize(2);
        String newer = tokenIn(sent.get(sent.size() - 1).getBody());

        reset(older, NEW_PASSWORD).andExpect(status().isBadRequest());
        reset(newer, NEW_PASSWORD).andExpect(status().isNoContent());
    }

    @Test
    void changingThePasswordVoidsOpenLinks() throws Exception {
        register("change@example.com");
        String access = JsonPath.read(login("change@example.com", PASSWORD)
                .andReturn().getResponse().getContentAsString(), "$.accessToken");
        forgot("change@example.com");
        String token = tokenIn(emails("PASSWORD_RESET").get(0).getBody());

        mockMvc.perform(put("/api/me/password")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + access)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"" + PASSWORD + "\",\"newPassword\":\"" + NEW_PASSWORD + "\"}"))
                .andExpect(status().isNoContent());

        reset(token, "An0ther-pass").andExpect(status().isBadRequest());
        assertThat(emails("PASSWORD_CHANGED")).hasSize(1);
    }

    @Test
    void weakNewPasswordIsRejected() throws Exception {
        register("weak@example.com");
        forgot("weak@example.com");
        String token = tokenIn(emails("PASSWORD_RESET").get(0).getBody());

        reset(token, "short").andExpect(status().isBadRequest());
        reset(token, NEW_PASSWORD).andExpect(status().isNoContent());
    }

    @Test
    void resetEmailBodyIsHiddenFromAdminsAndRedactedOnceSent() throws Exception {
        register("redact@example.com");
        forgot("redact@example.com");
        EmailOutbox email = emails("PASSWORD_RESET").get(0);

        mockMvc.perform(get("/api/admin/emails").param("status", "PENDING").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.kind == 'PASSWORD_RESET')].body",
                        Matchers.everyItem(Matchers.not(Matchers.containsString("#token=")))));

        outboxService.dispatchDue(Instant.now().plusSeconds(1), 50);

        EmailOutbox sent = outbox.findById(email.getId()).orElseThrow();
        assertThat(sent.getStatus()).isEqualTo(EmailOutbox.Status.SENT);
        assertThat(sent.getBody()).doesNotContain("#token=").contains("removido");
    }

    @Test
    void failedResetEmailCannotBeRetried() throws Exception {
        register("retry@example.com");
        forgot("retry@example.com");
        EmailOutbox email = emails("PASSWORD_RESET").get(0);
        jdbc.update("UPDATE email_outbox SET status = 'FAILED' WHERE id = ?", email.getId());

        mockMvc.perform(post("/api/admin/emails/{id}/retry", email.getId()).with(admin()))
                .andExpect(status().isConflict());
    }

    private List<EmailOutbox> emails(String kind) {
        return outbox.findAll().stream()
                .filter(e -> e.getKind().equals(kind))
                .sorted(java.util.Comparator.comparing(EmailOutbox::getId))
                .toList();
    }

    private static String tokenIn(String body) {
        Matcher matcher = TOKEN.matcher(body);
        assertThat(matcher.find()).as("reset link in e-mail body").isTrue();
        return matcher.group(1);
    }

    private void register(String email) throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\",\"fullName\":\"Test User\"}"))
                .andExpect(status().isCreated());
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

    private ResultActions forgot(String email) throws Exception {
        return mockMvc.perform(post("/api/auth/password/forgot")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\"}"));
    }

    private ResultActions reset(String token, String newPassword) throws Exception {
        return mockMvc.perform(post("/api/auth/password/reset")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + token + "\",\"newPassword\":\"" + newPassword + "\"}"));
    }
}
