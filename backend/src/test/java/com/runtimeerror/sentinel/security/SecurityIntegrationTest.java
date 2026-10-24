package com.runtimeerror.sentinel.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Sign-in, sessions, CSRF and API keys, against the real security setup and a real database.
 *
 * MockMvc sends requests straight into Spring without opening a network port, which makes
 * it quick and lets these tests look at sessions directly. csrf() and user(...) come from
 * spring-security-test: one adds a valid CSRF token, the other pretends someone is signed in.
 */
@SpringBootTest(properties = "sentinel.bootstrap.api-key=" + SecurityIntegrationTest.API_KEY)
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class SecurityIntegrationTest {

    static final String API_KEY = "security-test-key";

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine");

    @Container
    @ServiceConnection(name = "redis")
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    @Autowired
    MockMvc mvc;

    @Test
    void theDemoAnalystCanSignInAndTheSessionRemembersThem() throws Exception {
        MvcResult login = mvc.perform(post("/api/auth/login")
                        .param("username", "analyst")
                        .param("password", "sentinel-analyst")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("analyst"))
                .andExpect(jsonPath("$.role").value("ANALYST"))
                .andReturn();

        MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);

        mvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Neha Kapoor"));
    }

    @Test
    void aWrongPasswordIsRefused() throws Exception {
        mvc.perform(post("/api/auth/login")
                        .param("username", "analyst")
                        .param("password", "not-the-password")
                        .with(csrf()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("Sign-in failed"));
    }

    @Test
    void signingInWithoutTheCsrfTokenIsBlocked() throws Exception {
        mvc.perform(post("/api/auth/login")
                        .param("username", "analyst")
                        .param("password", "sentinel-analyst"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Request blocked"));
    }

    @Test
    void theConsoleApiNeedsSomeoneSignedIn() throws Exception {
        mvc.perform(get("/api/rules/active"))
                .andExpect(status().isUnauthorized());

        mvc.perform(get("/api/rules/active").with(user("someone").roles("ANALYST")))
                .andExpect(status().isOk());
    }

    @Test
    void aDecisionNeedsAValidApiKey() throws Exception {
        mvc.perform(post("/v1/decisions").contentType(MediaType.APPLICATION_JSON).content(payment()))
                .andExpect(status().isUnauthorized());

        mvc.perform(post("/v1/decisions").header(ApiKeys.HEADER, "not-a-real-key")
                        .contentType(MediaType.APPLICATION_JSON).content(payment()))
                .andExpect(status().isUnauthorized());

        mvc.perform(post("/v1/decisions").header(ApiKeys.HEADER, API_KEY)
                        .contentType(MediaType.APPLICATION_JSON).content(payment()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("APPROVE"));
    }

    @Test
    void aPersonSignedInToTheConsoleCannotMakeDecisions() throws Exception {
        mvc.perform(post("/v1/decisions").with(user("analyst").roles("ANALYST"))
                        .contentType(MediaType.APPLICATION_JSON).content(payment()))
                .andExpect(status().isForbidden());
    }

    @Test
    void healthIsPublicButMetricsAreForAdmins() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mvc.perform(get("/actuator/metrics").with(user("someone").roles("ANALYST"))).andExpect(status().isForbidden());
        mvc.perform(get("/actuator/metrics").with(user("boss").roles("ADMIN"))).andExpect(status().isOk());
    }

    private static String payment() {
        return """
                {"transactionId": "txn-%s", "cardId": "card-%s", "deviceId": "dev-1", "merchantId": "mer-1",
                 "amountMinor": 150000, "currency": "INR", "occurredAt": "2026-10-23T10:00:00Z"}
                """.formatted(UUID.randomUUID(), UUID.randomUUID());
    }
}
