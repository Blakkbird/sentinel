package com.runtimeerror.sentinel.audit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.runtimeerror.sentinel.rules.RuleSetTestClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.ArrayList;
import java.util.List;

import static com.runtimeerror.sentinel.rules.RuleSetTestClient.NEHA;
import static com.runtimeerror.sentinel.rules.RuleSetTestClient.version;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The audit log: every workflow step is recorded, newest first, and history cannot be rewritten. */
// Each integration test class starts its own Postgres and Redis. A property no other class
// sets gives it its own Spring context too, so it can never be handed a context that is still
// connected to another class's (already stopped) containers.
@SpringBootTest(properties = "sentinel.test.context=audit-log")
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class AuditLogIntegrationTest {

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

    @Autowired
    JdbcTemplate jdbc;

    RuleSetTestClient client;

    @BeforeEach
    void setUp() {
        client = new RuleSetTestClient(mvc, json);
    }

    @Test
    void draftingAndDiscardingAreRecordedWithWhoDidIt() throws Exception {
        int version = version(client.create(NEHA));
        client.step(NEHA, version, "discard", "{}").andExpect(status().isOk());

        JsonNode history = client.get(NEHA, "/api/rule-sets/" + version).get("history");

        assertThat(actions(history)).containsExactly("RULE_SET_DISCARDED", "RULE_SET_DRAFTED");
        assertThat(history.get(1).get("actor").asText()).isEqualTo("neha");
        assertThat(history.get(1).get("summary").asText()).contains("from the live version, v2");
    }

    @Test
    void theLogIsReadNewestFirstAPageAtATime() throws Exception {
        client.create(NEHA);
        client.create(NEHA);

        JsonNode first = client.get(NEHA, "/api/audit?limit=1");
        JsonNode second = client.get(NEHA, "/api/audit?limit=1&before=" + first.get("nextBefore").asLong());

        assertThat(first.get("items").get(0).get("id").asLong())
                .isGreaterThan(second.get("items").get(0).get("id").asLong());
    }

    @Test
    void theMigrationThatMovedTheRulesIsInTheLog() throws Exception {
        JsonNode page = client.get(NEHA, "/api/audit?limit=200");

        assertThat(actions(page.get("items"))).contains("RULES_MOVED_TO_DATABASE");
    }

    @Test
    void theDatabaseRefusesToRewriteHistory() {
        assertThatThrownBy(() -> jdbc.update("update audit_event set summary = 'rewritten'"))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("append-only");

        assertThatThrownBy(() -> jdbc.update("delete from rule_set_activation"))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("append-only");
    }

    private static List<String> actions(JsonNode events) {
        List<String> actions = new ArrayList<>();
        events.forEach(event -> actions.add(event.get("action").asText()));
        return actions;
    }
}
