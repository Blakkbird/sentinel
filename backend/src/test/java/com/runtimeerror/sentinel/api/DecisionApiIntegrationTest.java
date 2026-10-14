package com.runtimeerror.sentinel.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.runtimeerror.sentinel.engine.DecisionEngine;
import com.runtimeerror.sentinel.engine.FeatureVector;
import com.runtimeerror.sentinel.engine.Outcome;
import com.runtimeerror.sentinel.engine.Verdict;
import com.runtimeerror.sentinel.rules.ActiveRuleSet;
import com.runtimeerror.sentinel.rules.StarterRules;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests the whole application end to end: real HTTP, real Postgres, real Redis.
 *
 * Testcontainers starts a fresh Postgres and Redis in Docker for this test class and
 * throws them away afterwards. @ServiceConnection tells Spring Boot to connect to those
 * containers instead of the addresses in application.yml.
 *
 * Needs Docker. On a laptop without Docker the whole class is skipped (that is what
 * disabledWithoutDocker does) instead of failing. GitHub's CI machines always have Docker,
 * so these tests still run on every pull request: nobody can merge code that breaks them.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers(disabledWithoutDocker = true)
class DecisionApiIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    @ServiceConnection(name = "redis")
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    /**
     * Every decision in these tests happens at the same frozen instant, so one-minute
     * windows can never be split by the clock ticking over mid-test. This is exactly why
     * the application reads time from an injected Clock instead of calling Instant.now().
     */
    @TestConfiguration
    static class FrozenClock {
        @Bean
        @Primary
        Clock frozenClock() {
            return Clock.fixed(Instant.parse("2026-01-01T10:00:30Z"), ZoneOffset.UTC);
        }
    }

    @Autowired
    TestRestTemplate http;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    DecisionEngine engine;

    @Autowired
    ActiveRuleSet ruleSets;

    @Test
    void anOrdinaryPaymentIsApprovedAndRecorded() {
        String txn = id("txn");

        ResponseEntity<DecisionResponse> response =
                http.postForEntity("/v1/decisions", attempt(txn, id("card"), 1_500_00), DecisionResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().outcome()).isEqualTo(Outcome.APPROVE);
        assertThat(rowsFor(txn)).isEqualTo(1);
    }

    @Test
    void aRetryGetsTheOriginalDecisionBack() {
        Map<String, Object> body = attempt(id("txn"), id("card"), 2_000_00);

        DecisionResponse first = http.postForObject("/v1/decisions", body, DecisionResponse.class);
        DecisionResponse second = http.postForObject("/v1/decisions", body, DecisionResponse.class);

        assertThat(second.decisionId()).isEqualTo(first.decisionId());
        assertThat(rowsFor(first.transactionId())).isEqualTo(1);
    }

    @Test
    void aCardTestingBurstIsDeclinedOnTheSixthAttempt() {
        String card = id("card");
        List<Outcome> outcomes = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            outcomes.add(http.postForObject("/v1/decisions", attempt(id("txn"), card, 49_00),
                    DecisionResponse.class).outcome());
        }

        assertThat(outcomes.subList(0, 5)).containsOnly(Outcome.APPROVE);
        assertThat(outcomes.get(5)).isEqualTo(Outcome.DECLINE);
    }

    @Test
    void theShadowRuleWouldHaveDeclinedTheFourthAttempt() {
        String card = id("card");
        String fourth = null;
        for (int i = 1; i <= 4; i++) {
            String txn = id("txn");
            http.postForObject("/v1/decisions", attempt(txn, card, 49_00), DecisionResponse.class);
            fourth = txn;
        }

        DecisionDetail detail = http.getForObject("/v1/decisions/" + fourth, DecisionDetail.class);

        assertThat(detail.outcome()).isEqualTo(Outcome.APPROVE);
        assertThat(detail.shadowOutcome()).isEqualTo(Outcome.DECLINE);
    }

    @Test
    void aStoredDecisionReplaysToExactlyTheSameVerdict() {
        String txn = id("txn");
        http.postForObject("/v1/decisions", attempt(txn, id("card"), 75_000_00), DecisionResponse.class);

        DecisionDetail stored = http.getForObject("/v1/decisions/" + txn, DecisionDetail.class);
        Verdict replayed = engine.evaluate(ruleSets.current(), FeatureVector.of(stored.features()));

        assertThat(replayed.outcome()).isEqualTo(Outcome.STEP_UP).isEqualTo(stored.outcome());
        assertThat(replayed.trace()).isEqualTo(stored.trace());
    }

    @Test
    void anInvalidRequestIsRejectedWithEveryBadField() {
        Map<String, Object> body = attempt(id("txn"), id("card"), -5);
        body.put("currency", "rupees");

        // JsonNode is Jackson's generic "any JSON" type: handy when a test only needs to
        // poke at a couple of fields without writing a class for the whole response.
        ResponseEntity<JsonNode> response = http.postForEntity("/v1/decisions", body, JsonNode.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        JsonNode fields = response.getBody().get("fields");
        assertThat(fields.has("amountMinor")).isTrue();
        assertThat(fields.has("currency")).isTrue();
    }

    @Test
    void anUnknownTransactionIsNotFoundWithAProblemBody() {
        ResponseEntity<JsonNode> response = http.getForEntity("/v1/decisions/" + id("txn"), JsonNode.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().get("title").asText()).isEqualTo("Decision not found");
    }

    @Test
    void theActiveRulesAreReadableOverHttp() {
        RuleSetView view = http.getForObject("/v1/rules/active", RuleSetView.class);

        assertThat(view.version()).isEqualTo(StarterRules.VERSION);
        assertThat(view.rules())
                .filteredOn(rule -> rule.code().equals("VEL_CARD_1M"))
                .extracting(RuleSetView.RuleView::condition)
                .containsExactly("card.txnCount.1m > 5");
    }

    // ---- helpers ----------------------------------------------------------------------

    private static String id(String prefix) {
        return prefix + "-" + UUID.randomUUID();   // unique per test, so tests never share counters
    }

    private static Map<String, Object> attempt(String transactionId, String cardId, long amountMinor) {
        Map<String, Object> body = new HashMap<>();
        body.put("transactionId", transactionId);
        body.put("cardId", cardId);
        body.put("deviceId", "dev-" + cardId);
        body.put("merchantId", "mer-test");
        body.put("amountMinor", amountMinor);
        body.put("currency", "INR");
        body.put("occurredAt", "2026-01-01T10:00:30Z");
        return body;
    }

    private int rowsFor(String transactionId) {
        return jdbc.queryForObject(
                "select count(*) from decision_log where transaction_id = ?", Integer.class, transactionId);
    }
}
