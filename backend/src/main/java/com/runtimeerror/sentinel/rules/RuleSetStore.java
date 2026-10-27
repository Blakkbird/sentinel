package com.runtimeerror.sentinel.rules;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.runtimeerror.sentinel.engine.InvalidRuleSetException;
import com.runtimeerror.sentinel.engine.RuleDefinition;
import com.runtimeerror.sentinel.security.Actor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

/**
 * Reads and writes rule sets with plain SQL (JdbcTemplate), like the decision log.
 *
 * Why not JPA here, when the console's user table uses it? The interesting writes on this
 * table are conditional: "move version 3 from DRAFT to PENDING, but only if it is still a draft
 * at revision 4". That is one UPDATE with a WHERE clause, and the number of rows it changed
 * (0 or 1) says whether it worked. In SQL that is one readable line; with JPA it is a load, a
 * check and a save, with a race between the check and the save.
 *
 * The rules travel as JSON. Jackson turns the jsonb text into plain maps and lists, and
 * RuleSetJson turns those into rule definitions (and back again when writing).
 */
@Repository
public class RuleSetStore {

    private static final String SELECT = """
            select version, status, based_on, title, definition, revision,
                   created_by, created_by_name, created_at, updated_at, submitted_at,
                   reviewed_by, reviewed_by_name, reviewed_at, review_note
            from rule_set
            """;

    private static final String SELECT_ACTIVATIONS = """
            select id, version, kind, activated_by, activated_by_name, activated_at
            from rule_set_activation
            """;

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public RuleSetStore(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public Optional<StoredRuleSet> find(int version) {
        return jdbc.query(SELECT + "where version = ?", this::mapRow, version).stream().findFirst();
    }

    /** Every version, newest first. */
    public List<StoredRuleSet> findAll() {
        return jdbc.query(SELECT + "order by version desc", this::mapRow);
    }

    /** The newest activation, which names the live version. */
    public Optional<RuleSetActivation> latestActivation() {
        return jdbc.query(SELECT_ACTIVATIONS + "order by id desc limit 1", this::mapActivation).stream().findFirst();
    }

    /** The most recent activations, newest first: the live version, then what was live before it. */
    public List<RuleSetActivation> recentActivations(int limit) {
        return jdbc.query(SELECT_ACTIVATIONS + "order by id desc limit ?", this::mapActivation, limit);
    }

    /** Every time this one version went live, oldest first. */
    public List<RuleSetActivation> activationsOf(int version) {
        return jdbc.query(SELECT_ACTIVATIONS + "where version = ? order by id", this::mapActivation, version);
    }

    // ---- writes ---------------------------------------------------------------------
    //
    // Each change below is one UPDATE whose WHERE clause states what must still be true
    // ("still a draft", "still at revision 4"). The method returns whether a row changed. If
    // someone else got there first, nothing changes and the caller can say so. This is called
    // optimistic locking: no lock is held while a person edits, and a clash is detected at the
    // moment of saving instead.

    /** A new draft, numbered by the database. Returns its version number. */
    public int insertDraft(int basedOn, String title, List<RuleDefinition> rules, Actor author, Instant at) {
        Integer version = jdbc.queryForObject("""
                insert into rule_set (status, based_on, title, definition, revision,
                                      created_by, created_by_name, created_at, updated_at)
                values ('DRAFT', ?, ?, cast(? as jsonb), 1, ?, ?, ?, ?)
                returning version
                """, Integer.class,
                basedOn, title, toJson(rules), author.username(), author.displayName(), utc(at), utc(at));
        if (version == null) {
            throw new IllegalStateException("the database did not return the new draft's version");
        }
        return version;
    }

    /** Saves new rules into a draft, only if it is still a draft at the revision the editor started from. */
    public boolean updateDraft(int version, int expectedRevision, String title, List<RuleDefinition> rules, Instant at) {
        return jdbc.update("""
                update rule_set
                set title = ?, definition = cast(? as jsonb), revision = revision + 1, updated_at = ?
                where version = ? and status = 'DRAFT' and revision = ?
                """, title, toJson(rules), utc(at), version, expectedRevision) == 1;
    }

    /** Throws a draft away. It stays in the table, for the record. */
    public boolean discard(int version, Instant at) {
        return jdbc.update("""
                update rule_set set status = 'DISCARDED', updated_at = ?
                where version = ? and status = 'DRAFT'
                """, utc(at), version) == 1;
    }

    /** Draft to waiting-for-approval, only at the revision the author was looking at. */
    public boolean submit(int version, int expectedRevision, Instant at) {
        return jdbc.update("""
                update rule_set set status = 'PENDING', submitted_at = ?, updated_at = ?
                where version = ? and status = 'DRAFT' and revision = ?
                """, utc(at), utc(at), version, expectedRevision) == 1;
    }

    /** Waiting-for-approval back to draft, so its author can change it. */
    public boolean withdraw(int version, Instant at) {
        return jdbc.update("""
                update rule_set set status = 'DRAFT', submitted_at = null, updated_at = ?
                where version = ? and status = 'PENDING'
                """, utc(at), version) == 1;
    }

    /**
     * An admin's decision on a pending version: PUBLISHED or REJECTED. Only applies if it is
     * still pending at the revision the admin reviewed, so they approve exactly what they saw.
     */
    public boolean review(int version, int expectedRevision, RuleSetStatus outcome, Actor reviewer, String note, Instant at) {
        return jdbc.update("""
                update rule_set
                set status = ?, reviewed_by = ?, reviewed_by_name = ?, reviewed_at = ?, review_note = ?, updated_at = ?
                where version = ? and status = 'PENDING' and revision = ?
                """, outcome.name(), reviewer.username(), reviewer.displayName(), utc(at), note, utc(at),
                version, expectedRevision) == 1;
    }

    /** Records that a version went live. The newest row is always the live version. */
    public void insertActivation(int version, RuleSetActivation.Kind kind, Actor actor, Instant at) {
        jdbc.update("""
                insert into rule_set_activation (version, kind, activated_by, activated_by_name, activated_at)
                values (?, ?, ?, ?, ?)
                """, version, kind.name(), actor.username(), actor.displayName(), utc(at));
    }

    private String toJson(List<RuleDefinition> rules) {
        try {
            return json.writeValueAsString(RuleSetJson.write(rules));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("could not write rules as JSON", e);
        }
    }

    static OffsetDateTime utc(Instant at) {
        return at.atOffset(ZoneOffset.UTC);
    }

    // ---- row mapping ------------------------------------------------------------------

    private StoredRuleSet mapRow(ResultSet rs, int rowNumber) throws SQLException {
        int version = rs.getInt("version");
        return new StoredRuleSet(
                version,
                RuleSetStatus.valueOf(rs.getString("status")),
                rs.getObject("based_on", Integer.class),
                rs.getString("title"),
                RuleSetJson.read(readDefinition(version, rs.getString("definition"))),
                rs.getInt("revision"),
                rs.getString("created_by"),
                rs.getString("created_by_name"),
                instant(rs, "created_at"),
                instant(rs, "updated_at"),
                instant(rs, "submitted_at"),
                rs.getString("reviewed_by"),
                rs.getString("reviewed_by_name"),
                instant(rs, "reviewed_at"),
                rs.getString("review_note"));
    }

    private RuleSetActivation mapActivation(ResultSet rs, int rowNumber) throws SQLException {
        return new RuleSetActivation(
                rs.getLong("id"),
                rs.getInt("version"),
                RuleSetActivation.Kind.valueOf(rs.getString("kind")),
                rs.getString("activated_by"),
                rs.getString("activated_by_name"),
                instant(rs, "activated_at"));
    }

    private Object readDefinition(int version, String text) {
        try {
            return json.readValue(text, Object.class);
        } catch (JsonProcessingException | InvalidRuleSetException e) {
            // Every stored rule set was checked before it was written, so this means someone
            // edited the table by hand. Say exactly which version is broken.
            throw new IllegalStateException("stored rule set version " + version + " is unreadable", e);
        }
    }

    /** Postgres timestamptz to Instant; null stays null (for example, a draft has no reviewed_at). */
    static Instant instant(ResultSet rs, String column) throws SQLException {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }
}
