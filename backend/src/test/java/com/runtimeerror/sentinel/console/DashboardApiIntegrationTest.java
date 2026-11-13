package com.runtimeerror.sentinel.console;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.runtimeerror.sentinel.decision.DecisionLog;
import com.runtimeerror.sentinel.decision.StoredDecision;
import com.runtimeerror.sentinel.engine.FeatureVector;
import com.runtimeerror.sentinel.engine.Outcome;
import com.runtimeerror.sentinel.engine.RuleEvaluation;
import com.runtimeerror.sentinel.engine.RuleEvaluation.Result;
import com.runtimeerror.sentinel.engine.RuleMode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The dashboard endpoint against a real Postgres: known decisions go straight into the decision
 * log at chosen times, and every number that comes back is checked.
 *
 * The decisions are written with DecisionLog rather than sent through /v1, because the test
 * needs to choose what /v1 decides for itself: the time, the outcome, the compute time and
 * which rules fired.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class DashboardApiIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine");

    @Container
    @ServiceConnection(name = "redis")
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    /**
     * "Now" is frozen at 10:00:30, so the windows are known exactly:
     *   15m  09:46:00 up to 10:01:00
     *   1h   09:01:00 up to 10:01:00
     *   24h  10:15:00 the day before up to 10:15:00
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
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Autowired
    DecisionLog log;

    @Autowired
    JdbcTemplate jdbc;

    /** Every test starts from an empty decision log, so the numbers below are the only ones there. */
    @BeforeEach
    void emptyTheDecisionLog() {
        jdbc.update("delete from decision_log");
    }

    @Test
    void theLastHourCountsEveryDecisionInsideItAndNoneOutside() throws Exception {
        insertTheKnownDecisions();

        JsonNode dashboard = read("/api/dashboard");   // no window given: one hour

        assertThat(dashboard.get("window").asText()).isEqualTo("1h");
        assertThat(dashboard.get("generatedAt").asText()).isEqualTo("2026-01-01T10:00:30Z");
        assertThat(dashboard.get("bucketSeconds").asInt()).isEqualTo(60);

        JsonNode totals = dashboard.get("totals");
        assertThat(totals.get("decisions").asInt()).isEqualTo(5);
        assertThat(totals.get("approve").asInt()).isEqualTo(2);
        assertThat(totals.get("stepUp").asInt()).isEqualTo(2);
        assertThat(totals.get("decline").asInt()).isEqualTo(1);
        assertThat(totals.get("degraded").asInt()).isEqualTo(1);
        assertThat(totals.get("shadowChanges").asInt()).isEqualTo(1);

        // Compute times 100, 200, 300, 400, 1000: the middle one is 300. The 95th percentile
        // sits 80% of the way from the fourth value to the fifth: 400 + 0.8 x 600 = 880.
        // (Postgres does that sum in floating point and answers 879.9999999999999, hence "close to".)
        assertThat(dashboard.get("computeMicros").get("p50").asDouble()).isEqualTo(300.0);
        assertThat(dashboard.get("computeMicros").get("p95").asDouble()).isCloseTo(880.0, within(0.001));

        // Sixty one-minute buckets, 09:01 to 10:00. Three of them hold decisions; the other 57
        // are present and zero.
        JsonNode series = dashboard.get("series");
        assertThat(series).hasSize(60);
        assertThat(series.get(0).get("at").asText()).isEqualTo("2026-01-01T09:01:00Z");
        assertThat(series.get(59).get("at").asText()).isEqualTo("2026-01-01T10:00:00Z");
        assertThat(counts(series.get(0))).containsExactly(0, 0, 1);     // 09:01: one decline
        assertThat(counts(series.get(44))).containsExactly(0, 2, 0);    // 09:45: two step-ups
        assertThat(counts(series.get(59))).containsExactly(2, 0, 0);    // 10:00: two approvals
        assertThat(nonEmptyBuckets(series)).isEqualTo(3);

        assertThat(rules(dashboard)).containsExactly(
                "AMT_HIGH ACTIVE 3",
                "DEVICE_MANY_CARDS_1H SHADOW 1",   // a shadow rule: listed, and marked
                "VEL_CARD_1M ACTIVE 1");
    }

    @Test
    void fifteenMinutesIsANarrowerSliceOfTheSameDecisions() throws Exception {
        insertTheKnownDecisions();

        JsonNode dashboard = read("/api/dashboard?window=15m");

        assertThat(dashboard.get("window").asText()).isEqualTo("15m");
        assertThat(dashboard.get("totals").get("decisions").asInt()).isEqualTo(2);
        assertThat(dashboard.get("totals").get("approve").asInt()).isEqualTo(2);
        assertThat(dashboard.get("totals").get("stepUp").asInt()).isZero();
        assertThat(dashboard.get("totals").get("decline").asInt()).isZero();
        assertThat(dashboard.get("totals").get("degraded").asInt()).isZero();
        assertThat(dashboard.get("totals").get("shadowChanges").asInt()).isEqualTo(1);
        assertThat(dashboard.get("computeMicros").get("p50").asDouble()).isEqualTo(150.0);
        assertThat(dashboard.get("computeMicros").get("p95").asDouble()).isCloseTo(195.0, within(0.001));

        JsonNode series = dashboard.get("series");
        assertThat(series).hasSize(15);
        assertThat(series.get(0).get("at").asText()).isEqualTo("2026-01-01T09:46:00Z");
        assertThat(counts(series.get(14))).containsExactly(2, 0, 0);
        assertThat(nonEmptyBuckets(series)).isEqualTo(1);

        assertThat(rules(dashboard)).containsExactly("DEVICE_MANY_CARDS_1H SHADOW 1");
    }

    @Test
    void aDayUsesQuarterHourBuckets() throws Exception {
        insertTheKnownDecisions();

        JsonNode dashboard = read("/api/dashboard?window=24h");

        assertThat(dashboard.get("bucketSeconds").asInt()).isEqualTo(900);
        // Six: the five from the last hour and the one at 09:00:59. The one from the day
        // before, a second ahead of the window, stays out.
        assertThat(dashboard.get("totals").get("decisions").asInt()).isEqualTo(6);
        assertThat(dashboard.get("totals").get("decline").asInt()).isEqualTo(2);
        assertThat(dashboard.get("computeMicros").get("p50").asDouble()).isEqualTo(350.0);
        assertThat(dashboard.get("computeMicros").get("p95").asDouble()).isCloseTo(4000.0, within(0.001));

        JsonNode series = dashboard.get("series");
        assertThat(series).hasSize(96);
        assertThat(series.get(0).get("at").asText()).isEqualTo("2025-12-31T10:15:00Z");
        assertThat(series.get(95).get("at").asText()).isEqualTo("2026-01-01T10:00:00Z");
        assertThat(counts(series.get(91))).containsExactly(0, 0, 2);    // 09:00 to 09:15
        assertThat(counts(series.get(94))).containsExactly(0, 2, 0);    // 09:45 to 10:00
        assertThat(counts(series.get(95))).containsExactly(2, 0, 0);    // 10:00 to 10:15
        assertThat(nonEmptyBuckets(series)).isEqualTo(3);

        assertThat(rules(dashboard)).containsExactly(
                "AMT_HIGH ACTIVE 3",
                "VEL_CARD_1M ACTIVE 2",
                "DEVICE_MANY_CARDS_1H SHADOW 1");
    }

    @Test
    void anEmptyWindowIsAllZerosAndNoPercentiles() throws Exception {
        JsonNode dashboard = read("/api/dashboard?window=1h");

        JsonNode totals = dashboard.get("totals");
        for (String field : List.of("decisions", "approve", "stepUp", "decline", "degraded", "shadowChanges")) {
            assertThat(totals.get(field).asInt()).as(field).isZero();
        }
        assertThat(dashboard.get("computeMicros").get("p50").isNull()).isTrue();
        assertThat(dashboard.get("computeMicros").get("p95").isNull()).isTrue();
        assertThat(dashboard.get("series")).hasSize(60);
        assertThat(nonEmptyBuckets(dashboard.get("series"))).isZero();
        assertThat(dashboard.get("topRules")).isEmpty();
    }

    @Test
    void onlyTheEightRulesThatFiredMostAreListed() throws Exception {
        // Ten rules, RULE_0 to RULE_9. RULE_9 fires in all ten decisions, RULE_8 in nine, and so
        // on down to RULE_0 in one. The two that fired least are left out.
        for (int decision = 0; decision < 10; decision++) {
            List<RuleEvaluation> trace = new ArrayList<>();
            for (int rule = 0; rule <= decision; rule++) {
                trace.add(fired("RULE_" + (9 - decision + rule), RuleMode.ACTIVE));
            }
            insert("2026-01-01T10:00:00Z", Outcome.DECLINE, Outcome.DECLINE, false, 100, trace);
        }

        assertThat(rules(read("/api/dashboard?window=15m"))).containsExactly(
                "RULE_9 ACTIVE 10", "RULE_8 ACTIVE 9", "RULE_7 ACTIVE 8", "RULE_6 ACTIVE 7",
                "RULE_5 ACTIVE 6", "RULE_4 ACTIVE 5", "RULE_3 ACTIVE 4", "RULE_2 ACTIVE 3");
    }

    @Test
    void anUnknownWindowIsABadRequest() throws Exception {
        mvc.perform(get("/api/dashboard").param("window", "7d").with(analyst()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid request"))
                .andExpect(jsonPath("$.detail").value("'7d' is not a valid value for window. Use 15m, 1h or 24h."));
    }

    @Test
    void theDashboardNeedsSomeoneSignedIn() throws Exception {
        mvc.perform(get("/api/dashboard")).andExpect(status().isUnauthorized());
    }

    // ---- helpers ----------------------------------------------------------------------

    /**
     * Seven decisions around the edges of the windows.
     *
     *   when                 outcome   shadow    micros  notes
     *   10:00:10             APPROVE   APPROVE      100
     *   10:00:20             APPROVE   DECLINE      200  a shadow rule fired: a shadow change
     *   09:45:00             STEP_UP   STEP_UP      300  degraded
     *   09:45:59.999         STEP_UP   STEP_UP      400  last millisecond of the 09:45 bucket
     *   09:01:00             DECLINE   DECLINE     1000  exactly where the hour starts: inside
     *   09:00:59.999         DECLINE   DECLINE     5000  a millisecond before it: only in 24h
     *   10:14:59 day before  DECLINE   DECLINE     9000  a second before the 24h window: nowhere
     */
    private void insertTheKnownDecisions() {
        insert("2026-01-01T10:00:10Z", Outcome.APPROVE, Outcome.APPROVE, false, 100,
                List.of(notFired("AMT_HIGH", RuleMode.ACTIVE), notFired("DEVICE_MANY_CARDS_1H", RuleMode.SHADOW)));
        insert("2026-01-01T10:00:20Z", Outcome.APPROVE, Outcome.DECLINE, false, 200,
                List.of(notFired("AMT_HIGH", RuleMode.ACTIVE), fired("DEVICE_MANY_CARDS_1H", RuleMode.SHADOW)));
        insert("2026-01-01T09:45:00Z", Outcome.STEP_UP, Outcome.STEP_UP, true, 300,
                List.of(fired("AMT_HIGH", RuleMode.ACTIVE), abstained("VEL_CARD_1M", RuleMode.ACTIVE)));
        insert("2026-01-01T09:45:59.999Z", Outcome.STEP_UP, Outcome.STEP_UP, false, 400,
                List.of(fired("AMT_HIGH", RuleMode.ACTIVE), notFired("VEL_CARD_1M", RuleMode.ACTIVE)));
        insert("2026-01-01T09:01:00Z", Outcome.DECLINE, Outcome.DECLINE, false, 1000,
                List.of(fired("AMT_HIGH", RuleMode.ACTIVE), fired("VEL_CARD_1M", RuleMode.ACTIVE)));
        insert("2026-01-01T09:00:59.999Z", Outcome.DECLINE, Outcome.DECLINE, false, 5000,
                List.of(notFired("AMT_HIGH", RuleMode.ACTIVE), fired("VEL_CARD_1M", RuleMode.ACTIVE)));
        insert("2025-12-31T10:14:59Z", Outcome.DECLINE, Outcome.DECLINE, true, 9000,
                List.of(fired("AMT_HIGH", RuleMode.ACTIVE), fired("VEL_CARD_1M", RuleMode.ACTIVE)));
    }

    private void insert(String decidedAt, Outcome outcome, Outcome shadowOutcome, boolean degraded,
                        int computeMicros, List<RuleEvaluation> trace) {
        Instant at = Instant.parse(decidedAt);
        log.insert(new StoredDecision(UUID.randomUUID(), "txn-" + UUID.randomUUID(), "card-1", "dev-1", "mer-test",
                49_00, "INR", at, at, outcome, shadowOutcome, List.of(), 2, degraded,
                FeatureVector.of(Map.of("txn.amountMinor", 4900.0)), trace, computeMicros));
    }

    private static RuleEvaluation fired(String code, RuleMode mode) {
        return new RuleEvaluation(code, mode, Result.FIRED, Outcome.DECLINE, code);
    }

    private static RuleEvaluation notFired(String code, RuleMode mode) {
        return new RuleEvaluation(code, mode, Result.NOT_FIRED, Outcome.DECLINE, code);
    }

    private static RuleEvaluation abstained(String code, RuleMode mode) {
        return new RuleEvaluation(code, mode, Result.ABSTAINED, Outcome.DECLINE, code);
    }

    private JsonNode read(String path) throws Exception {
        String body = mvc.perform(get(path).with(analyst()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body);
    }

    private static RequestPostProcessor analyst() {
        return user("analyst").roles("ANALYST");
    }

    /** One bucket as approve, step-up, decline. */
    private static List<Integer> counts(JsonNode point) {
        return List.of(point.get("approve").asInt(), point.get("stepUp").asInt(), point.get("decline").asInt());
    }

    private static long nonEmptyBuckets(JsonNode series) {
        long nonEmpty = 0;
        for (JsonNode point : series) {
            if (point.get("approve").asInt() + point.get("stepUp").asInt() + point.get("decline").asInt() > 0) {
                nonEmpty++;
            }
        }
        return nonEmpty;
    }

    /** Each top rule as "CODE MODE count", in the order returned. */
    private static List<String> rules(JsonNode dashboard) {
        List<String> rules = new ArrayList<>();
        dashboard.get("topRules").forEach(rule -> rules.add(
                rule.get("ruleCode").asText() + " " + rule.get("mode").asText() + " " + rule.get("count").asInt()));
        return rules;
    }
}
