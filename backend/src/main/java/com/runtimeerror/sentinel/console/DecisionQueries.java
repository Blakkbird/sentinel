package com.runtimeerror.sentinel.console;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.runtimeerror.sentinel.engine.Outcome;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

/**
 * Searching the decision log for the console. Plain SQL, read-only.
 *
 * The query is assembled from the filters that are set, with named parameters (:outcome,
 * :q) instead of question marks so each one is easy to follow. Values are never pasted into
 * the SQL text itself; they travel separately, which is what makes SQL injection impossible.
 */
@Repository
public class DecisionQueries {

    private static final String COLUMNS = """
            select decision_id, transaction_id, card_id, device_id, merchant_id, amount_minor, currency,
                   decided_at, outcome, shadow_outcome, reason_codes, rule_set_version, degraded, compute_micros
            from decision_log
            """;

    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() { };

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper json;

    public DecisionQueries(NamedParameterJdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    /** Newest first. Pass the previous page's cursor as "after" to get the page after it. */
    public DecisionPage search(DecisionFilter filter, DecisionCursor after, int limit) {
        StringBuilder sql = new StringBuilder(COLUMNS).append("where true\n");
        MapSqlParameterSource params = new MapSqlParameterSource();

        if (filter.outcome() != null) {
            sql.append("  and outcome = :outcome\n");
            params.addValue("outcome", filter.outcome().name());
        }
        if (filter.query() != null) {
            sql.append("  and (transaction_id = :q or card_id = :q or device_id = :q or merchant_id = :q)\n");
            params.addValue("q", filter.query());
        }
        if (filter.degradedOnly()) {
            sql.append("  and degraded\n");
        }
        if (filter.shadowChangesOnly()) {
            sql.append("  and outcome <> shadow_outcome\n");
        }
        if (after != null) {
            // The first test lets Postgres use the index on decided_at. The second is the exact
            // rule: strictly older than the bookmark, with the decision id breaking ties.
            sql.append("  and decided_at <= :afterAt and (decided_at, decision_id) < (:afterAt, :afterId)\n");
            params.addValue("afterAt", after.decidedAt().atOffset(ZoneOffset.UTC));
            params.addValue("afterId", after.decisionId());
        }
        sql.append("order by decided_at desc, decision_id desc\n");
        sql.append("limit :limit");
        params.addValue("limit", limit + 1);   // one extra row says whether another page exists

        List<DecisionSummary> rows = jdbc.query(sql.toString(), params, this::mapRow);
        if (rows.size() <= limit) {
            return new DecisionPage(rows, null);
        }
        List<DecisionSummary> page = rows.subList(0, limit);
        DecisionSummary last = page.get(page.size() - 1);
        return new DecisionPage(List.copyOf(page), new DecisionCursor(last.decidedAt(), last.decisionId()).encode());
    }

    private DecisionSummary mapRow(ResultSet rs, int rowNumber) throws SQLException {
        return new DecisionSummary(
                rs.getObject("decision_id", UUID.class),
                rs.getString("transaction_id"),
                rs.getString("card_id"),
                rs.getString("device_id"),
                rs.getString("merchant_id"),
                rs.getLong("amount_minor"),
                rs.getString("currency"),
                rs.getObject("decided_at", OffsetDateTime.class).toInstant(),
                Outcome.valueOf(rs.getString("outcome")),
                Outcome.valueOf(rs.getString("shadow_outcome")),
                readCodes(rs.getString("reason_codes")),
                rs.getInt("rule_set_version"),
                rs.getBoolean("degraded"),
                rs.getInt("compute_micros"));
    }

    private List<String> readCodes(String text) {
        try {
            return json.readValue(text, STRING_LIST);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("could not read stored reason codes", e);
        }
    }
}
