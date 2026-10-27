package com.runtimeerror.sentinel.lab;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.runtimeerror.sentinel.rules.RuleSetTestClient;
import com.runtimeerror.sentinel.security.ApiKeys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

import static com.runtimeerror.sentinel.rules.RuleSetTestClient.NEHA;
import static com.runtimeerror.sentinel.rules.RuleSetTestClient.version;
import static com.runtimeerror.sentinel.rules.RuleSetTestClient.withMode;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The Rule Lab end to end: real payments go in through /v1, an analyst drafts a stricter rule,
 * and the lab shows exactly which of those payments it would have declined.
 */
@SpringBootTest(properties = "sentinel.bootstrap.api-key=" + RuleLabIntegrationTest.API_KEY)
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class RuleLabIntegrationTest {

    static final String API_KEY = "rule-lab-test-key";

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine");

    @Container
    @ServiceConnection(name = "redis")
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    /** Every payment at the same instant, so all five land in the same one-minute bucket. */
    @TestConfiguration
    static class FrozenClock {
        @Bean
        @Primary
        Clock frozenClock() {
            return Clock.fixed(Instant.parse("2026-01-01T10:00:30Z"), ZoneOffset.UTC);
        }
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Test
    void promotingTheStrictCardRuleWouldHaveDeclinedTheFourthAndFifthAttempts() throws Exception {
        String card = "card-" + UUID.randomUUID();
        for (int i = 0; i < 5; i++) {
            decide(card);   // the live rules (v2) approve all five: VEL_CARD_1M needs more than 5
        }

        RuleSetTestClient client = new RuleSetTestClient(mvc, json);
        JsonNode draft = client.create(NEHA);
        int version = version(draft);
        client.save(NEHA, version, 1, "Stricter card testing", withMode(draft, "VEL_CARD_1M_STRICT", "ACTIVE"))
                .andExpect(status().isOk());

        JsonNode report = client.get(NEHA, "/api/rule-sets/" + version + "/replay?decisions=100");

        assertThat(report.get("decisions").asInt()).isEqualTo(5);
        assertThat(report.get("liveVersion").asInt()).isEqualTo(2);
        assertThat(report.get("changed").asInt()).isEqualTo(2);
        assertThat(report.get("moves").get(0).get("from").asText()).isEqualTo("APPROVE");
        assertThat(report.get("moves").get(0).get("to").asText()).isEqualTo("DECLINE");
        // All five share one timestamp, so their order in the strip is not fixed; the counts are.
        assertThat(report.get("versionStrip").asText().chars().filter(c -> c == 'D').count()).isEqualTo(2);
        // Replaying version 2 on each decision's facts gives back exactly what was recorded.
        assertThat(report.get("check").get("matched").asInt()).isEqualTo(5);
    }

    private void decide(String card) throws Exception {
        Map<String, Object> body = Map.of(
                "transactionId", "txn-" + UUID.randomUUID(), "cardId", card, "deviceId", "dev-" + card,
                "merchantId", "mer-test", "amountMinor", 49_00, "currency", "INR", "occurredAt", "2026-01-01T10:00:30Z");
        mvc.perform(post("/v1/decisions").header(ApiKeys.HEADER, API_KEY)
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)))
                .andExpect(status().isOk());
    }

}
