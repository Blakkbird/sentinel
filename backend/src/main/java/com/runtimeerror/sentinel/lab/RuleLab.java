package com.runtimeerror.sentinel.lab;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.runtimeerror.sentinel.engine.DecisionEngine;
import com.runtimeerror.sentinel.engine.FeatureVector;
import com.runtimeerror.sentinel.engine.Outcome;
import com.runtimeerror.sentinel.engine.RuleSet;
import com.runtimeerror.sentinel.features.Features;
import com.runtimeerror.sentinel.rules.ActiveRuleSet;
import com.runtimeerror.sentinel.rules.RuleSetNotFoundException;
import com.runtimeerror.sentinel.rules.RuleSetStore;
import com.runtimeerror.sentinel.rules.StoredRuleSet;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The Rule Lab: "what would this version have done to the last N payments?"
 *
 * Reads the most recent decisions (their stored facts), then hands everything to Replay, which
 * does the comparing. Read-only: it never changes a decision or a rule.
 *
 * Cost: one query that reads N rows, then N x 2 engine runs of a few microseconds each. At the
 * maximum of 5,000 decisions that is well under a second, most of it the round trip to Neon.
 */
@Service
public class RuleLab {

    public static final int DEFAULT_DECISIONS = 1_000;
    public static final int MAX_DECISIONS = 5_000;
    private static final int EXAMPLES = 50;

    // The index on decided_at (V1) lets Postgres read the newest rows first and stop at the limit.
    private static final String RECENT = """
            select transaction_id, decided_at, card_id, amount_minor, currency,
                   outcome, shadow_outcome, rule_set_version, features
            from decision_log
            order by decided_at desc, decision_id desc
            limit ?
            """;

    private static final TypeReference<Map<String, Double>> FEATURE_MAP = new TypeReference<>() { };

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final RuleSetStore store;
    private final ActiveRuleSet active;
    private final DecisionEngine engine;

    public RuleLab(JdbcTemplate jdbc, ObjectMapper json, RuleSetStore store, ActiveRuleSet active, DecisionEngine engine) {
        this.jdbc = jdbc;
        this.json = json;
        this.store = store;
        this.active = active;
        this.engine = engine;
    }

    public ReplayReport replay(int version, int decisions) {
        StoredRuleSet stored = store.find(version).orElseThrow(() -> new RuleSetNotFoundException(version));
        // A draft can still change, so it is compiled fresh here rather than kept anywhere.
        RuleSet tried = RuleSet.compile(version, stored.rules(), Features.CATALOGUE);
        int limit = Math.max(1, Math.min(decisions, MAX_DECISIONS));

        List<Replay.Recorded> oldestFirst = new ArrayList<>(jdbc.query(RECENT, this::mapRow, limit).reversed());
        return Replay.run(oldestFirst, active.current(), tried, active::find, engine, EXAMPLES);
    }

    private Replay.Recorded mapRow(ResultSet rs, int rowNumber) throws SQLException {
        return new Replay.Recorded(
                rs.getString("transaction_id"),
                rs.getObject("decided_at", OffsetDateTime.class).toInstant(),
                rs.getString("card_id"),
                rs.getLong("amount_minor"),
                rs.getString("currency"),
                Outcome.valueOf(rs.getString("outcome")),
                Outcome.valueOf(rs.getString("shadow_outcome")),
                rs.getInt("rule_set_version"),
                FeatureVector.of(readFeatures(rs.getString("features"))));
    }

    private Map<String, Double> readFeatures(String text) {
        try {
            return json.readValue(text, FEATURE_MAP);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("could not read stored features", e);
        }
    }
}
