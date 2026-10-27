package com.runtimeerror.sentinel.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.runtimeerror.sentinel.security.Actor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

/**
 * The audit log: an append-only record of who did what, and when. The table refuses updates and
 * deletes (see V4), so this class can only add rows and read them.
 *
 * record() joins whatever transaction its caller has open. That is the point: the rule change
 * and its audit row are saved together, or not at all.
 */
@Repository
public class AuditLog {

    public static final String RULE_SET = "RULE_SET";

    private static final String SELECT = """
            select id, occurred_at, actor, actor_name, action, target_type, target_id, summary, details
            from audit_event
            """;

    private static final TypeReference<Map<String, Object>> DETAILS = new TypeReference<>() { };

    /** One page of the log, newest first. nextBefore is the id to ask "before" for the next page, or null. */
    public record Page(List<AuditEvent> items, Long nextBefore) {
    }

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public AuditLog(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public void record(Actor actor, AuditAction action, String targetType, String targetId, String summary,
                       Map<String, Object> details, Instant at) {
        jdbc.update("""
                insert into audit_event (occurred_at, actor, actor_name, action, target_type, target_id, summary, details)
                values (?, ?, ?, ?, ?, ?, ?, cast(? as jsonb))
                """, at.atOffset(ZoneOffset.UTC), actor.username(), actor.displayName(), action.name(),
                targetType, targetId, summary, toJson(details));
    }

    /**
     * Newest first, keyset-paged by id like the decision search: "before" is the id of the last
     * row already shown. Ids only ever grow, so they order events exactly as they happened.
     */
    public Page page(Long before, int limit) {
        List<AuditEvent> rows = before == null
                ? jdbc.query(SELECT + "order by id desc limit ?", this::mapRow, limit + 1)
                : jdbc.query(SELECT + "where id < ? order by id desc limit ?", this::mapRow, before, limit + 1);
        if (rows.size() <= limit) {
            return new Page(rows, null);
        }
        List<AuditEvent> page = List.copyOf(rows.subList(0, limit));
        return new Page(page, page.getLast().id());
    }

    /** Everything that happened to one thing, newest first. */
    public List<AuditEvent> forTarget(String targetType, String targetId) {
        return jdbc.query(SELECT + "where target_type = ? and target_id = ? order by id desc",
                this::mapRow, targetType, targetId);
    }

    private AuditEvent mapRow(ResultSet rs, int rowNumber) throws SQLException {
        return new AuditEvent(
                rs.getLong("id"),
                rs.getObject("occurred_at", OffsetDateTime.class).toInstant(),
                rs.getString("actor"),
                rs.getString("actor_name"),
                rs.getString("action"),
                rs.getString("target_type"),
                rs.getString("target_id"),
                rs.getString("summary"),
                fromJson(rs.getString("details")));
    }

    private String toJson(Map<String, Object> details) {
        try {
            return json.writeValueAsString(details);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("could not write audit details", e);
        }
    }

    private Map<String, Object> fromJson(String text) {
        try {
            return json.readValue(text, DETAILS);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("could not read audit details", e);
        }
    }
}
