package com.runtimeerror.sentinel.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.runtimeerror.sentinel.engine.DecisionEngine;
import com.runtimeerror.sentinel.engine.FeatureVector;
import com.runtimeerror.sentinel.engine.Outcome;
import com.runtimeerror.sentinel.engine.Verdict;
import com.runtimeerror.sentinel.rules.ActiveRuleSet;
import com.runtimeerror.sentinel.rules.StarterRules;
import com.runtimeerror.sentinel.security.ApiKeys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
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
 *
 * Every call sends an API key, like a real payment system. The key is set for this test
 * through the properties below, and BootstrapData registers it at startup.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "sentinel.bootstrap.api-key=" + DecisionApiIntegrationTest.API_KEY)
@Testcontainers(disabledWithoutDocker = true)
class DecisionApiIntegrationTest {

    static final String API_KEY = "decision-api-test-key";

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine");

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

        ResponseEntity<DecisionResponse> response = decide(attempt(txn, id("card"), 1_500_00));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().outcome()).isEqualTo(Outcome.APPROVE);
        assertThat(rowsFor(txn)).isEqualTo(1);
    }

    @Test
    void aRetryGetsTheOriginalDecisionBack() {
        Map<String, Object> body = attempt(id("txn"), id("card"), 2_000_00);

        DecisionResponse first = decide(body).getBody();
        DecisionResponse second = decide(body).getBody();

        assertThat(second.decisionId()).isEqualTo(first.decisionId());
        assertThat(rowsFor(first.transactionId())).isEqualTo(1);
    }

    @Test
    void aCardTestingBurstIsDeclinedOnTheSixthAttempt() {
        String card = id("card");
        List<Outcome> outcomes = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            outcomes.add(decide(attempt(id("txn"), card, 49_00)).getBody().outcome());
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
            decide(attempt(txn, card, 49_00));
            fourth = txn;
        }

        DecisionDetail detail = get("/v1/decisions/" + fourth, DecisionDetail.class).getBody();

        assertThat(detail.outcome()).isEqualTo(Outcome.APPROVE);
        assertThat(detail.shadowOutcome()).isEqualTo(Outcome.DECLINE);
    }

    @Test
    void aStoredDecisionReplaysToExactlyTheSameVerdict() {
        String txn = id("txn");
        decide(attempt(txn, id("card"), 75_000_00));

        DecisionDetail stored = get("/v1/decisions/" + txn, DecisionDetail.class).getBody();
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
        ResponseEntity<JsonNode> response = post(body, JsonNode.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        JsonNode fields = response.getBody().get("fields");
        assertThat(fields.has("amountMinor")).isTrue();
        assertThat(fields.has("currency")).isTrue();
    }

    @Test
    void anUnknownTransactionIsNotFoundWithAProblemBody() {
        ResponseEntity<JsonNode> response = get("/v1/decisions/" + id("txn"), JsonNode.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().get("title").asText()).isEqualTo("Decision not found");
    }

    @Test
    void theActiveRulesAreReadableOverHttp() {
        RuleSetView view = get("/v1/rules/active", RuleSetView.class).getBody();

        assertThat(view.version()).isEqualTo(StarterRules.VERSION);
        assertThat(view.rules())
                .filteredOn(rule -> rule.code().equals("VEL_CARD_1M"))
                .extracting(RuleSetView.RuleView::condition)
                .containsExactly("card.txnCount.1m > 5");
    }

    @Test
    void aCallWithoutAnApiKeyIsTurnedAway() {
        ResponseEntity<JsonNode> response = http.postForEntity("/v1/decisions", attempt(id("txn"), id("card"), 1_00), JsonNode.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody().get("title").asText()).isEqualTo("Not signed in");
    }

    // ---- helpers ----------------------------------------------------------------------

    private ResponseEntity<DecisionResponse> decide(Map<String, Object> body) {
        return post(body, DecisionResponse.class);
    }

    private <T> ResponseEntity<T> post(Object body, Class<T> type) {
        return http.exchange("/v1/decisions", HttpMethod.POST, new HttpEntity<>(body, withApiKey()), type);
    }

    private <T> ResponseEntity<T> get(String path, Class<T> type) {
        return http.exchange(path, HttpMethod.GET, new HttpEntity<>(withApiKey()), type);
    }

    private static HttpHeaders withApiKey() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(ApiKeys.HEADER, API_KEY);
        return headers;
    }

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
