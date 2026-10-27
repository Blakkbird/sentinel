package com.runtimeerror.sentinel.rules;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The console's rule endpoints, wrapped in small methods so each test reads like the steps a
 * person takes. Shared by every rule set integration test.
 *
 * Changes are POST or PUT, so each one carries a CSRF token (csrf()), exactly as the console's
 * own requests do. user(...) pretends someone is signed in, with the roles given.
 */
public final class RuleSetTestClient {

    public static final RequestPostProcessor NEHA = user("neha").roles("ANALYST");
    public static final RequestPostProcessor RAVI = user("ravi").roles("ANALYST");
    public static final RequestPostProcessor ARJUN = user("arjun").roles("ADMIN");
    public static final RequestPostProcessor MEERA = user("meera").roles("ADMIN");

    private final MockMvc mvc;
    private final ObjectMapper json;

    public RuleSetTestClient(MockMvc mvc, ObjectMapper json) {
        this.mvc = mvc;
        this.json = json;
    }

    /** Starts a draft from the live version and returns it. */
    public JsonNode create(RequestPostProcessor who) throws Exception {
        return read(mvc.perform(post("/api/rule-sets").with(who).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isCreated()));
    }

    public ResultActions save(RequestPostProcessor who, int version, int revision, String title, ArrayNode rules) throws Exception {
        ObjectNode body = json.createObjectNode();
        body.put("revision", revision);
        body.put("title", title);
        body.set("rules", rules);
        return mvc.perform(put("/api/rule-sets/" + version).with(who).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body.toString()));
    }

    /** POST /api/rule-sets/{version}/{step}, for discard, submit, approve and the rest. */
    public ResultActions step(RequestPostProcessor who, int version, String step, String body) throws Exception {
        return mvc.perform(post("/api/rule-sets/" + version + "/" + step).with(who).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    public JsonNode get(RequestPostProcessor who, String path) throws Exception {
        return read(mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(path)
                        .with(who))
                .andExpect(status().isOk()));
    }

    public JsonNode read(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString());
    }

    /** A draft whose rules are copied and then edited: takes a version's rules, ready to send back. */
    public static ArrayNode rulesOf(JsonNode detail) {
        ArrayNode rules = detail.get("rules").deepCopy();
        rules.forEach(rule -> ((ObjectNode) rule).remove("conditionText"));   // a view-only field
        return rules;
    }

    public static ArrayNode withMode(JsonNode detail, String code, String mode) {
        ArrayNode rules = rulesOf(detail);
        rules.forEach(rule -> {
            if (rule.get("code").asText().equals(code)) {
                ((ObjectNode) rule).put("mode", mode);
            }
        });
        return rules;
    }

    public static JsonNode rule(JsonNode detail, String code) {
        for (JsonNode rule : detail.get("rules")) {
            if (rule.get("code").asText().equals(code)) {
                return rule;
            }
        }
        throw new AssertionError("no rule " + code + " in " + detail);
    }

    public static JsonNode permission(JsonNode detail, String action) {
        for (JsonNode permission : detail.get("permissions")) {
            if (permission.get("action").asText().equals(action)) {
                return permission;
            }
        }
        throw new AssertionError("no permission " + action + " in " + detail);
    }

    public static int version(JsonNode detail) {
        return detail.get("version").asInt();
    }

    public static int revision(JsonNode detail) {
        return detail.get("revision").asInt();
    }
}
