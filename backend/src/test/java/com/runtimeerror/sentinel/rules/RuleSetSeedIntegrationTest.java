package com.runtimeerror.sentinel.rules;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Migration V3 copied versions 1 and 2 of the rules from Java into the database as JSON.
 * These tests prove the copy is exact, and that the application now runs on the database's rules.
 */
// Each integration test class starts its own Postgres and Redis. A property no other class
// sets gives it its own Spring context too, so it can never be handed a context that is still
// connected to another class's (already stopped) containers.
@SpringBootTest(properties = "sentinel.test.context=rule-set-seed")
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class RuleSetSeedIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine");

    @Container
    @ServiceConnection(name = "redis")
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    @Autowired
    RuleSetStore store;

    @Autowired
    ActiveRuleSet active;

    @Autowired
    MockMvc mvc;

    @Test
    void version2InTheDatabaseIsExactlyTheJavaStarterRules() {
        StoredRuleSet v2 = store.find(2).orElseThrow();

        assertThat(v2.status()).isEqualTo(RuleSetStatus.PUBLISHED);
        assertThat(v2.rules()).isEqualTo(StarterRules.definitions());
    }

    @Test
    void version1IsTheSixRulesThatCameBeforeTheDeviceSignals() {
        List<String> v1Codes = store.find(1).orElseThrow().rules().stream().map(r -> r.code()).toList();

        assertThat(v1Codes).containsExactly(
                "AMT_HIGH", "VEL_CARD_1M", "VEL_CARD_1M_STRICT", "SPEND_CARD_1H", "DEVICE_BURST_1H", "DEGRADED_FALLBACK");
    }

    @Test
    void version2IsLiveAndEveryPublishedVersionCanBeFound() {
        assertThat(store.latestActivation().orElseThrow().version()).isEqualTo(2);
        assertThat(active.current().version()).isEqualTo(2);
        assertThat(active.find(1)).isPresent();
        assertThat(active.find(99)).isEmpty();
    }

    @Test
    void theConsoleListsVersionsAndShowsOne() throws Exception {
        mvc.perform(get("/api/rule-sets").with(user("analyst").roles("ANALYST")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.liveVersion").value(2))
                .andExpect(jsonPath("$.previousVersion").value(1))
                .andExpect(jsonPath("$.items[0].version").value(2))
                .andExpect(jsonPath("$.items[0].live").value(true));

        mvc.perform(get("/api/rule-sets/1").with(user("analyst").roles("ANALYST")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.live").value(false))
                .andExpect(jsonPath("$.rules[1].conditionText").value("card.txnCount.1m > 5"))
                .andExpect(jsonPath("$.rules[1].condition.type").value("compare"));

        mvc.perform(get("/api/rule-sets/99").with(user("analyst").roles("ANALYST")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Rule set not found"));
    }
}
