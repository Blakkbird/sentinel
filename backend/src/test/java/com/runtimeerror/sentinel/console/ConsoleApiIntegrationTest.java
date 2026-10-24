package com.runtimeerror.sentinel.console;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.runtimeerror.sentinel.security.ApiKeys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The console's decision endpoints, end to end: payments go in through /v1 with an API key,
 * then an analyst searches for them and opens one through /api.
 */
@SpringBootTest(properties = "sentinel.bootstrap.api-key=" + ConsoleApiIntegrationTest.API_KEY)
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class ConsoleApiIntegrationTest {

    static final String API_KEY = "console-test-key";

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine");

    @Container
    @ServiceConnection(name = "redis")
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Test
    void searchFindsADecisionByCardAndItsPageExplainsIt() throws Exception {
        String card = id("card");
        String txn = decide(card, 75_000_00);   // above Rs 50,000, so AMT_HIGH steps it up

        JsonNode page = read(get("/api/decisions").param("q", card));
        assertThat(page.get("items")).hasSize(1);
        assertThat(page.get("items").get(0).get("transactionId").asText()).isEqualTo(txn);
        assertThat(page.get("items").get(0).get("outcome").asText()).isEqualTo("STEP_UP");

        JsonNode detail = read(get("/api/decisions/" + txn));
        JsonNode amountRule = rule(detail, "AMT_HIGH");
        assertThat(amountRule.get("result").asText()).isEqualTo("FIRED");
        assertThat(amountRule.get("evidence").get(0).get("actual").asDouble()).isEqualTo(75_000_00);
        assertThat(amountRule.get("evidence").get(0).get("holds").asBoolean()).isTrue();
        assertThat(detail.get("facts").get(0).get("name").asText()).isEqualTo("txn.amountMinor");
    }

    @Test
    void pagesWalkBackThroughTimeWithACursor() throws Exception {
        String card = id("card");
        String first = decide(card, 100_00);
        String second = decide(card, 200_00);
        String third = decide(card, 300_00);

        JsonNode newest = read(get("/api/decisions").param("q", card).param("limit", "2"));
        assertThat(ids(newest)).containsExactly(third, second);

        JsonNode older = read(get("/api/decisions").param("q", card).param("limit", "2")
                .param("cursor", newest.get("nextCursor").asText()));
        assertThat(ids(older)).containsExactly(first);
        assertThat(older.get("nextCursor").isNull()).isTrue();
    }

    @Test
    void filtersByOutcome() throws Exception {
        String card = id("card");
        decide(card, 100_00);
        String big = decide(card, 80_000_00);

        JsonNode stepUps = read(get("/api/decisions").param("q", card).param("outcome", "STEP_UP"));

        assertThat(ids(stepUps)).containsExactly(big);
    }

    @Test
    void badParametersAreBadRequests() throws Exception {
        mvc.perform(get("/api/decisions").param("outcome", "MAYBE").with(analyst()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid parameter"));

        mvc.perform(get("/api/decisions").param("cursor", "%%%").with(analyst()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid request"));
    }

    @Test
    void anUnknownTransactionIsNotFound() throws Exception {
        mvc.perform(get("/api/decisions/" + id("txn")).with(analyst()))
                .andExpect(status().isNotFound());
    }

    @Test
    void theLiveFeedNeedsSomeoneSignedIn() throws Exception {
        mvc.perform(get("/api/stream/decisions")).andExpect(status().isUnauthorized());
    }

    // ---- helpers ----------------------------------------------------------------------

    private String decide(String card, long amountMinor) throws Exception {
        String txn = id("txn");
        Map<String, Object> body = Map.of(
                "transactionId", txn, "cardId", card, "deviceId", "dev-" + card, "merchantId", "mer-test",
                "amountMinor", amountMinor, "currency", "INR", "occurredAt", "2026-10-23T10:00:00Z");
        mvc.perform(post("/v1/decisions").header(ApiKeys.HEADER, API_KEY)
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)))
                .andExpect(status().isOk());
        return txn;
    }

    private JsonNode read(MockHttpServletRequestBuilder request) throws Exception {
        String body = mvc.perform(request.with(analyst()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body);
    }

    private static RequestPostProcessor analyst() {
        return user("analyst").roles("ANALYST");
    }

    private static JsonNode rule(JsonNode detail, String code) {
        for (JsonNode rule : detail.get("rules")) {
            if (rule.get("code").asText().equals(code)) {
                return rule;
            }
        }
        throw new AssertionError("no rule " + code + " in " + detail);
    }

    private static List<String> ids(JsonNode page) {
        List<String> ids = new ArrayList<>();
        page.get("items").forEach(item -> ids.add(item.get("transactionId").asText()));
        return ids;
    }

    private static String id(String prefix) {
        return prefix + "-" + UUID.randomUUID();
    }
}
