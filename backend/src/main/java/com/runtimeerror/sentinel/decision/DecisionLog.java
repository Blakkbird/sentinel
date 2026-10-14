package com.runtimeerror.sentinel.decision;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.runtimeerror.sentinel.engine.FeatureVector;
import com.runtimeerror.sentinel.engine.Outcome;
import com.runtimeerror.sentinel.engine.RuleEvaluation;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads and writes the decision_log table with plain SQL.
 *
 * Why plain SQL (JdbcTemplate) here instead of JPA/Hibernate?
 * This is the hot path, and the table is append-only: we insert and we look up, nothing
 * else. JPA's strengths (tracking changes to objects, loading related objects) are of no
 * use here, and its conveniences hide real costs. For example, calling save() on an
 * entity whose id you set yourself makes Hibernate run a SELECT first to check whether
 * the row exists, doubling the database work on every decision. With plain SQL, the
 * query you read is exactly the query that runs.
 *
 * JPA arrives in stage 5 for the console's cases and users, where it genuinely helps.
 */
@Repository
public class DecisionLog {

    private static final String INSERT = """
            insert into decision_log (
                decision_id, transaction_id, card_id, device_id, merchant_id,
                amount_minor, currency, occurred_at, decided_at,
                outcome, shadow_outcome, reason_codes, rule_set_version, degraded,
                features, trace, compute_micros)
            values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, cast(? as jsonb), ?, ?, cast(? as jsonb), cast(? as jsonb), ?)
            """;

    private static final String SELECT_BY_TRANSACTION = """
            select decision_id, transaction_id, card_id, device_id, merchant_id,
                   amount_minor, currency, occurred_at, decided_at,
                   outcome, shadow_outcome, reason_codes, rule_set_version, degraded,
                   features, trace, compute_micros
            from decision_log
            where transaction_id = ?
            """;

    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() { };
    private static final TypeReference<Map<String, Double>> FEATURE_MAP = new TypeReference<>() { };
    private static final TypeReference<List<RuleEvaluation>> TRACE = new TypeReference<>() { };

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public DecisionLog(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    /**
     * Inserts one decision. If the transaction id already exists, the unique constraint
     * makes this throw DuplicateKeyException, which DecisionService handles.
     */
    public void insert(StoredDecision d) {
        jdbc.update(INSERT,
                d.decisionId(), d.transactionId(), d.cardId(), d.deviceId(), d.merchantId(),
                d.amountMinor(), d.currency(), utc(d.occurredAt()), utc(d.decidedAt()),
                d.outcome().name(), d.shadowOutcome().name(), toJson(d.reasonCodes()),
                d.ruleSetVersion(), d.degraded(),
                toJson(d.features().asMap()), toJson(d.trace()), d.computeMicros());
    }

    public Optional<StoredDecision> findByTransactionId(String transactionId) {
        List<StoredDecision> rows = jdbc.query(SELECT_BY_TRANSACTION, this::mapRow, transactionId);
        return rows.stream().findFirst();
    }

    private StoredDecision mapRow(ResultSet rs, int rowNumber) throws SQLException {
        return new StoredDecision(
                rs.getObject("decision_id", UUID.class),
                rs.getString("transaction_id"),
                rs.getString("card_id"),
                rs.getString("device_id"),
                rs.getString("merchant_id"),
                rs.getLong("amount_minor"),
                rs.getString("currency"),
                rs.getObject("occurred_at", OffsetDateTime.class).toInstant(),
                rs.getObject("decided_at", OffsetDateTime.class).toInstant(),
                Outcome.valueOf(rs.getString("outcome")),
                Outcome.valueOf(rs.getString("shadow_outcome")),
                fromJson(rs.getString("reason_codes"), STRING_LIST),
                rs.getInt("rule_set_version"),
                rs.getBoolean("degraded"),
                FeatureVector.of(fromJson(rs.getString("features"), FEATURE_MAP)),
                fromJson(rs.getString("trace"), TRACE),
                rs.getInt("compute_micros"));
    }

    private static OffsetDateTime utc(java.time.Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }

    private String toJson(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("could not serialise " + value, e);
        }
    }

    private <T> T fromJson(String text, TypeReference<T> type) {
        try {
            return json.readValue(text, type);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("could not read stored JSON", e);
        }
    }
}
