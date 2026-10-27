package com.runtimeerror.sentinel.rules;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
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

import static com.runtimeerror.sentinel.rules.RuleSetTestClient.NEHA;
import static com.runtimeerror.sentinel.rules.RuleSetTestClient.RAVI;
import static com.runtimeerror.sentinel.rules.RuleSetTestClient.permission;
import static com.runtimeerror.sentinel.rules.RuleSetTestClient.rule;
import static com.runtimeerror.sentinel.rules.RuleSetTestClient.rulesOf;
import static com.runtimeerror.sentinel.rules.RuleSetTestClient.version;
import static com.runtimeerror.sentinel.rules.RuleSetTestClient.withMode;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Drafting: start a draft, change it, and the guard rails around who may change what. */
// Each integration test class starts its own Postgres and Redis. A property no other class
// sets gives it its own Spring context too, so it can never be handed a context that is still
// connected to another class's (already stopped) containers.
@SpringBootTest(properties = "sentinel.test.context=rule-set-drafts")
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class RuleSetDraftIntegrationTest {

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

    RuleSetTestClient client;

    @BeforeEach
    void setUp() {
        client = new RuleSetTestClient(mvc, json);
    }

    @Test
    void anAnalystStartsADraftFromTheLiveRulesAndSavesAChange() throws Exception {
        JsonNode draft = client.create(NEHA);

        assertThat(version(draft)).isGreaterThanOrEqualTo(3);
        assertThat(draft.get("status").asText()).isEqualTo("DRAFT");
        assertThat(draft.get("basedOn").asInt()).isEqualTo(2);
        assertThat(draft.get("rules")).hasSize(8);
        assertThat(permission(draft, "EDIT").get("allowed").asBoolean()).isTrue();

        JsonNode saved = client.read(client.save(NEHA, version(draft), 1, "Decline device enumeration",
                        withMode(draft, "DEVICE_MANY_CARDS_1H", "ACTIVE"))
                .andExpect(status().isOk()));

        assertThat(saved.get("revision").asInt()).isEqualTo(2);
        assertThat(saved.get("title").asText()).isEqualTo("Decline device enumeration");
        assertThat(rule(saved, "DEVICE_MANY_CARDS_1H").get("mode").asText()).isEqualTo("ACTIVE");
    }

    @Test
    void aSaveFromAnOutOfDateCopyIsRefusedInsteadOfOverwritingNewerWork() throws Exception {
        JsonNode draft = client.create(NEHA);

        client.save(NEHA, version(draft), 1, "From the first tab", rulesOf(draft)).andExpect(status().isOk());

        client.save(NEHA, version(draft), 1, "From an older tab", rulesOf(draft))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Not possible right now"));
    }

    @Test
    void onlyTheAuthorCanChangeADraft() throws Exception {
        JsonNode draft = client.create(NEHA);

        client.save(RAVI, version(draft), 1, "Not mine to change", rulesOf(draft))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail", containsString("who started this draft")));

        JsonNode seenByRavi = client.get(RAVI, "/api/rule-sets/" + version(draft));
        assertThat(permission(seenByRavi, "EDIT").get("allowed").asBoolean()).isFalse();
    }

    @Test
    void anInvalidRuleSetIsRefusedWithEveryProblemAndNothingIsStored() throws Exception {
        JsonNode draft = client.create(NEHA);
        ArrayNode rules = rulesOf(draft);
        ((ObjectNode) rules.get(0).get("condition")).put("feature", "card.txnCount.1min");   // a typo
        ((ObjectNode) rules.get(1)).put("code", "lower_case");                                // breaks the naming rule

        client.save(NEHA, version(draft), 1, "Broken", rules)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Rule set is invalid"))
                .andExpect(jsonPath("$.problems", hasSize(2)))
                .andExpect(jsonPath("$.problems[0]", containsString("unknown feature 'card.txnCount.1min'")));

        JsonNode unchanged = client.get(NEHA, "/api/rule-sets/" + version(draft));
        assertThat(unchanged.get("revision").asInt()).isEqualTo(1);
    }

    @Test
    void aDiscardedDraftCanNoLongerBeChanged() throws Exception {
        JsonNode draft = client.create(NEHA);

        client.step(NEHA, version(draft), "discard", "{}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DISCARDED"));

        client.save(NEHA, version(draft), 1, "Too late", rulesOf(draft))
                .andExpect(status().isConflict());
    }
}
