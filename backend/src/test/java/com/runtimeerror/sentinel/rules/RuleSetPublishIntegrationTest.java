package com.runtimeerror.sentinel.rules;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.runtimeerror.sentinel.security.ApiKeys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;
import java.util.UUID;

import static com.runtimeerror.sentinel.rules.RuleSetTestClient.ARJUN;
import static com.runtimeerror.sentinel.rules.RuleSetTestClient.MEERA;
import static com.runtimeerror.sentinel.rules.RuleSetTestClient.NEHA;
import static com.runtimeerror.sentinel.rules.RuleSetTestClient.RAVI;
import static com.runtimeerror.sentinel.rules.RuleSetTestClient.rulesOf;
import static com.runtimeerror.sentinel.rules.RuleSetTestClient.version;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Submit, approve and publish, reject, and roll back: the whole path from an analyst's idea to
 * live rules, with each guard rail along the way.
 *
 * These tests change which version is live, and they run in no fixed order. So none of them
 * assumes version 2 is live: each starts from whatever is live and changes it from there.
 */
@SpringBootTest(properties = "sentinel.bootstrap.api-key=" + RuleSetPublishIntegrationTest.API_KEY)
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class RuleSetPublishIntegrationTest {

    static final String API_KEY = "publish-test-key";

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
    ActiveRuleSet active;

    RuleSetTestClient client;

    @BeforeEach
    void setUp() {
        client = new RuleSetTestClient(mvc, json);
    }

    @Test
    void anAnalystsChangeGoesLiveOnlyWhenAnAdminApprovesIt() throws Exception {
        int liveBefore = active.current().version();
        JsonNode draft = client.create(NEHA);
        int v = version(draft);
        client.save(NEHA, v, 1, "Watch a new pattern", withExtraRule(draft)).andExpect(status().isOk());
        client.step(NEHA, v, "submit", "{\"revision\": 2}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"));

        // An analyst cannot approve anything, not even someone else's change.
        client.step(RAVI, v, "approve", "{\"revision\": 2}").andExpect(status().isForbidden());
        assertThat(active.current().version()).isEqualTo(liveBefore);

        client.step(ARJUN, v, "approve", "{\"revision\": 2, \"note\": \"Rule Lab shows only bot traffic changes\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.live").value(true));

        // Live at once, with no restart: the very next payment is decided by the new version.
        assertThat(active.current().version()).isEqualTo(v);
        assertThat(decide().get("ruleSetVersion").asInt()).isEqualTo(v);

        JsonNode history = client.get(NEHA, "/api/rule-sets/" + v).get("history");
        assertThat(history.get(0).get("action").asText()).isEqualTo("RULE_SET_PUBLISHED");
        assertThat(history.get(0).get("actor").asText()).isEqualTo("arjun");
        assertThat(history.get(0).get("details").get("note").asText()).contains("bot traffic");
    }

    @Test
    void anAdminCannotApproveTheirOwnChange() throws Exception {
        JsonNode draft = client.create(ARJUN);
        int v = version(draft);
        client.save(ARJUN, v, 1, "My own change", withExtraRule(draft)).andExpect(status().isOk());
        client.step(ARJUN, v, "submit", "{\"revision\": 2}").andExpect(status().isOk());

        client.step(ARJUN, v, "approve", "{\"revision\": 2}")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail", containsString("maker-checker")));

        client.step(MEERA, v, "approve", "{\"revision\": 2}").andExpect(status().isOk());
    }

    @Test
    void aChangeBasedOnAVersionThatIsNoLongerLiveCannotBePublished() throws Exception {
        JsonNode first = client.create(NEHA);    // both drafts start from the same live version
        JsonNode second = client.create(RAVI);
        publish(first);

        client.save(RAVI, version(second), 1, "Late change", withExtraRule(second)).andExpect(status().isOk());
        client.step(RAVI, version(second), "submit", "{\"revision\": 2}").andExpect(status().isOk());

        client.step(ARJUN, version(second), "approve", "{\"revision\": 2}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail", containsString("is live now")));
    }

    @Test
    void oneClickRollbackMakesThePreviousVersionLiveAgain() throws Exception {
        int before = active.current().version();
        int v = publish(client.create(NEHA));
        assertThat(active.current().version()).isEqualTo(v);

        client.step(NEHA, before, "activate", "{}").andExpect(status().isForbidden());   // analysts cannot
        client.step(ARJUN, before, "activate", "{\"note\": \"Too many false declines\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.live").value(true));

        assertThat(active.current().version()).isEqualTo(before);
        assertThat(decide().get("ruleSetVersion").asInt()).isEqualTo(before);

        JsonNode listing = client.get(NEHA, "/api/rule-sets");
        assertThat(listing.get("liveVersion").asInt()).isEqualTo(before);
        assertThat(listing.get("previousVersion").asInt()).isEqualTo(v);
    }

    @Test
    void aRejectionNeedsAReasonAndStaysOnRecord() throws Exception {
        JsonNode draft = client.create(NEHA);
        int v = version(draft);
        client.save(NEHA, v, 1, "Risky", withExtraRule(draft)).andExpect(status().isOk());
        client.step(NEHA, v, "submit", "{\"revision\": 2}").andExpect(status().isOk());

        client.step(ARJUN, v, "reject", "{\"revision\": 2}").andExpect(status().isBadRequest());
        client.step(ARJUN, v, "reject", "{\"revision\": 2, \"note\": \"Replay shows 40 false declines\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.reviewNote").value("Replay shows 40 false declines"));
    }

    @Test
    void aDraftThatChangesNothingCannotBeSent() throws Exception {
        JsonNode draft = client.create(NEHA);

        client.step(NEHA, version(draft), "submit", "{\"revision\": 1}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail", containsString("makes no changes")));
    }

    @Test
    void theAuthorCanTakeAPendingVersionBackToChangeIt() throws Exception {
        JsonNode draft = client.create(NEHA);
        int v = version(draft);
        client.save(NEHA, v, 1, "Second thoughts", withExtraRule(draft)).andExpect(status().isOk());
        client.step(NEHA, v, "submit", "{\"revision\": 2}").andExpect(status().isOk());

        client.save(NEHA, v, 2, "Frozen while pending", withExtraRule(draft)).andExpect(status().isConflict());
        client.step(NEHA, v, "withdraw", "{}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"));
    }

    // ---- helpers ----------------------------------------------------------------------

    /** Drafted by Neha, approved by Arjun. Returns the version, now live. */
    private int publish(JsonNode draft) throws Exception {
        int v = version(draft);
        client.save(NEHA, v, 1, "A tested change", withExtraRule(draft)).andExpect(status().isOk());
        client.step(NEHA, v, "submit", "{\"revision\": 2}").andExpect(status().isOk());
        client.step(ARJUN, v, "approve", "{\"revision\": 2}").andExpect(status().isOk());
        return v;
    }

    /**
     * The draft's rules plus one new shadow rule with a unique code. Always a real change,
     * whatever earlier tests have published, so "makes no changes" never gets in the way.
     */
    private static ArrayNode withExtraRule(JsonNode draft) {
        ArrayNode rules = rulesOf(draft);
        ObjectNode extra = rules.get(0).deepCopy();
        String code = "TEST_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        extra.put("code", code);
        extra.put("reasonCode", code);
        extra.put("mode", "SHADOW");
        rules.add(extra);
        return rules;
    }

    private JsonNode decide() throws Exception {
        Map<String, Object> body = Map.of(
                "transactionId", "txn-" + UUID.randomUUID(), "cardId", "card-" + UUID.randomUUID(),
                "amountMinor", 1_500_00, "currency", "INR", "occurredAt", "2026-10-27T10:00:00Z");
        return client.read(mvc.perform(post("/v1/decisions").header(ApiKeys.HEADER, API_KEY)
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)))
                .andExpect(status().isOk()));
    }
}
