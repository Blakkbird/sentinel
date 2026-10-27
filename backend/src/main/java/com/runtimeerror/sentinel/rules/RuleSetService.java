package com.runtimeerror.sentinel.rules;

import com.runtimeerror.sentinel.audit.AuditAction;
import com.runtimeerror.sentinel.audit.AuditLog;
import com.runtimeerror.sentinel.engine.InvalidRuleSetException;
import com.runtimeerror.sentinel.engine.RuleDefinition;
import com.runtimeerror.sentinel.engine.RuleSet;
import com.runtimeerror.sentinel.features.Features;
import com.runtimeerror.sentinel.security.Actor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;

/**
 * The rule workflow: who may do what to which version, and doing it.
 *
 *   draft --submit--> pending --approve (an admin, not the author)--> published, and live
 *                        |   \--reject (an admin, with a reason)--> rejected
 *                        \--withdraw (the author)--> back to draft
 *   any published version --activate (an admin)--> live again: a rollback
 *
 * Every "may this person do this, right now?" question is answered in one method, refusal().
 * The console asks the same method which buttons to show (permissions()), so a button the
 * console offers is always one the server will accept, and the reason a button is missing is
 * the same sentence the server would send back.
 *
 * Nothing invalid is ever stored: a draft's rules are read with RuleSetJson (shape) and checked
 * with RuleSet.compile (meaning) before every save. So every row in rule_set compiles.
 *
 * Every step that changes a version's status also writes an audit row, inside one transaction
 * (TransactionTemplate): the change and its record are saved together or not at all. Saving a
 * draft's edits is not audited; what matters for the record is what was submitted, approved and
 * published, and those versions can no longer change.
 */
@Service
public class RuleSetService {

    /** Things a person can do to a version. The console shows a button for each one allowed. */
    public enum Action {
        EDIT,
        DISCARD,
        SUBMIT,
        WITHDRAW,
        APPROVE,
        REJECT,
        ACTIVATE
    }

    /** Whether this person may take this action now, and if not, why not. */
    public record Permission(Action action, boolean allowed, String reason) {
    }

    /** Why an action is refused. forbidden: never for this person (403). Otherwise: not right now (409). */
    record Refusal(boolean forbidden, String reason) {
        RuleSetRefusedException exception() {
            return new RuleSetRefusedException(forbidden, reason);
        }
    }

    static final int MAX_TITLE_LENGTH = 120;
    static final int MAX_NOTE_LENGTH = 500;

    private final RuleSetStore store;
    private final ActiveRuleSet active;
    private final AuditLog audit;
    private final TransactionTemplate tx;
    private final Clock clock;
    private final ApplicationEventPublisher events;

    /**
     * Publishing and rolling back change which version is live: a row in the database and the
     * rule set in memory. Two of them running at once could interleave so that the database says
     * one version is live while memory holds the other. This lock makes them take turns.
     * Decisions never take it: they read ActiveRuleSet's AtomicReference and never wait.
     *
     * A lock inside one JVM is enough while Sentinel runs as one process. Several instances would
     * need the database to arbitrate (a row lock or an advisory lock): gap 15 in the notes.
     */
    private final ReentrantLock liveChanges = new ReentrantLock();

    public RuleSetService(RuleSetStore store, ActiveRuleSet active, AuditLog audit, TransactionTemplate tx,
                          Clock clock, ApplicationEventPublisher events) {
        this.store = store;
        this.active = active;
        this.audit = audit;
        this.tx = tx;
        this.clock = clock;
        this.events = events;
    }

    public StoredRuleSet get(int version) {
        return store.find(version).orElseThrow(() -> new RuleSetNotFoundException(version));
    }

    /** The version deciding payments right now: the newest activation. */
    public int liveVersion() {
        return store.latestActivation().map(RuleSetActivation::version)
                .orElseThrow(() -> new IllegalStateException("no rule set is live"));
    }

    // ---- drafts -----------------------------------------------------------------------

    /**
     * Starts a draft holding a copy of the rules in version "from" (the live version when from
     * is null). Anyone signed in may start one, and becomes its author.
     *
     * Whatever it was copied from, a draft is always based on the version live right now. That
     * is the version it will replace when published, so it is what the console compares it
     * with, and publishing is refused if another version has gone live in the meantime.
     */
    public StoredRuleSet createDraft(Integer from, String title, Actor author) {
        int live = liveVersion();
        StoredRuleSet source = get(from == null ? live : from);
        String name = title == null || title.isBlank()
                ? (source.version() == live ? "Changes to version " + live : "Copy of version " + source.version())
                : cleanTitle(title);
        Instant now = clock.instant();
        Integer version = tx.execute(status -> {
            int created = store.insertDraft(live, name, source.rules(), author, now);
            audit(author, AuditAction.RULE_SET_DRAFTED, created,
                    "Started draft v" + created + (source.version() == live
                            ? " from the live version, v" + live
                            : " as a copy of v" + source.version() + " (v" + live + " is live)"),
                    Map.of("copiedFrom", source.version(), "basedOn", live), now);
            return created;
        });
        return get(version);
    }

    /**
     * Replaces a draft's title and rules. "revision" is the revision the editor started from:
     * if the draft has been saved since (another tab, say), the save is refused rather than
     * silently overwriting that work.
     */
    public StoredRuleSet saveDraft(int version, int revision, String title, Object rules, Actor author) {
        StoredRuleSet draft = get(version);
        require(Action.EDIT, draft, author);
        String name = cleanTitle(title);

        List<RuleDefinition> definitions = RuleSetJson.readRules(rules);      // shape: fields and types
        RuleSet.compile(version, definitions, Features.CATALOGUE);            // meaning: names, features, duplicates

        if (!store.updateDraft(version, revision, name, definitions, clock.instant())) {
            throw changedMeanwhile(version);
        }
        return get(version);
    }

    public StoredRuleSet discard(int version, Actor author) {
        StoredRuleSet draft = get(version);
        require(Action.DISCARD, draft, author);
        Instant now = clock.instant();
        tx.executeWithoutResult(status -> {
            if (!store.discard(version, now)) {
                throw changedMeanwhile(version);
            }
            audit(author, AuditAction.RULE_SET_DISCARDED, version, "Discarded draft v" + version, Map.of(), now);
        });
        return get(version);
    }

    // ---- approval ---------------------------------------------------------------------

    /** The author sends a draft for approval. From now on it is frozen. */
    public StoredRuleSet submit(int version, int revision, Actor author) {
        StoredRuleSet draft = get(version);
        require(Action.SUBMIT, draft, author);
        Instant now = clock.instant();
        tx.executeWithoutResult(status -> {
            if (!store.submit(version, revision, now)) {
                throw changedMeanwhile(version);
            }
            audit(author, AuditAction.RULE_SET_SUBMITTED, version,
                    "Sent v" + version + " for approval: " + draft.title(), Map.of(), now);
        });
        return get(version);
    }

    /** The author takes a pending version back, to change it. */
    public StoredRuleSet withdraw(int version, Actor author) {
        StoredRuleSet pending = get(version);
        require(Action.WITHDRAW, pending, author);
        Instant now = clock.instant();
        tx.executeWithoutResult(status -> {
            if (!store.withdraw(version, now)) {
                throw changedMeanwhile(version);
            }
            audit(author, AuditAction.RULE_SET_WITHDRAWN, version,
                    "Took v" + version + " back from approval to change it", Map.of(), now);
        });
        return get(version);
    }

    /**
     * An admin who is not the author approves a pending version, and it goes live at once.
     *
     * The order matters. First the database records it (status, activation, audit row, in one
     * transaction). Only after that commits does the in-memory rule set switch over. If the
     * database step fails, nothing has changed anywhere; memory never runs ahead of the record.
     */
    public StoredRuleSet approve(int version, int revision, String note, Actor admin) {
        liveChanges.lock();
        try {
            StoredRuleSet pending = get(version);
            require(Action.APPROVE, pending, admin);
            if (pending.revision() != revision) {
                throw new RuleSetRefusedException(false, "Version " + version
                        + " changed after you opened it. Reload and review it again before approving.");
            }
            RuleSet compiled = RuleSet.compile(version, pending.rules(), Features.CATALOGUE);
            int replaced = liveVersion();
            String reason = cleanNote(note);
            Instant now = clock.instant();

            tx.executeWithoutResult(status -> {
                if (!store.review(version, revision, RuleSetStatus.PUBLISHED, admin, reason, now)) {
                    throw changedMeanwhile(version);
                }
                store.insertActivation(version, RuleSetActivation.Kind.PUBLISH, admin, now);
                audit(admin, AuditAction.RULE_SET_PUBLISHED, version,
                        "Approved and published v" + version + ", replacing v" + replaced,
                        details(replaced, reason), now);
            });

            active.activate(compiled);
            events.publishEvent(new RuleSetActivated(version, replaced, RuleSetActivation.Kind.PUBLISH,
                    admin.displayName(), pending.title()));
            return get(version);
        } finally {
            liveChanges.unlock();
        }
    }

    /** An admin who is not the author turns a pending version down, saying why. */
    public StoredRuleSet reject(int version, int revision, String note, Actor admin) {
        StoredRuleSet pending = get(version);
        require(Action.REJECT, pending, admin);
        String reason = cleanNote(note);
        Instant now = clock.instant();
        tx.executeWithoutResult(status -> {
            if (!store.review(version, revision, RuleSetStatus.REJECTED, admin, reason, now)) {
                throw changedMeanwhile(version);
            }
            audit(admin, AuditAction.RULE_SET_REJECTED, version, "Rejected v" + version, details(null, reason), now);
        });
        return get(version);
    }

    // ---- rollback ---------------------------------------------------------------------

    /**
     * Makes an older published version live again: the one-click rollback. One admin is enough,
     * with no second approval, because in an incident speed matters most, and because the version
     * being restored was itself approved when it was first published. It is still audited.
     */
    public StoredRuleSet activate(int version, String note, Actor admin) {
        liveChanges.lock();
        try {
            StoredRuleSet target = get(version);
            require(Action.ACTIVATE, target, admin);
            RuleSet compiled = active.find(version).orElseThrow(() -> new RuleSetRefusedException(false,
                    "Version " + version + " uses a fact this server no longer has, so it cannot be made live."));
            int replaced = liveVersion();
            String reason = cleanNote(note);
            Instant now = clock.instant();

            tx.executeWithoutResult(status -> {
                store.insertActivation(version, RuleSetActivation.Kind.ROLLBACK, admin, now);
                audit(admin, AuditAction.RULE_SET_ROLLED_BACK, version,
                        "Made v" + version + " live again, replacing v" + replaced, details(replaced, reason), now);
            });

            active.activate(compiled);
            events.publishEvent(new RuleSetActivated(version, replaced, RuleSetActivation.Kind.ROLLBACK,
                    admin.displayName(), target.title()));
            return get(version);
        } finally {
            liveChanges.unlock();
        }
    }

    // ---- who may do what --------------------------------------------------------------

    /** Every action, and whether this person may take it on this version now. */
    public List<Permission> permissions(StoredRuleSet ruleSet, Actor actor) {
        int live = liveVersion();
        List<Permission> permissions = new ArrayList<>();
        for (Action action : Action.values()) {
            Refusal refusal = refusal(action, ruleSet, actor, live);
            permissions.add(new Permission(action, refusal == null, refusal == null ? null : refusal.reason()));
        }
        return permissions;
    }

    private void require(Action action, StoredRuleSet ruleSet, Actor actor) {
        Refusal refusal = refusal(action, ruleSet, actor, liveVersion());
        if (refusal != null) {
            throw refusal.exception();
        }
    }

    /** Why this person may not take this action on this version right now, or null if they may. */
    private Refusal refusal(Action action, StoredRuleSet rs, Actor actor, int live) {
        boolean author = rs.createdBy().equals(actor.username());
        int v = rs.version();
        return switch (action) {
            case EDIT, DISCARD, SUBMIT -> {
                if (rs.status() != RuleSetStatus.DRAFT) {
                    yield new Refusal(false, "Version " + v + " is " + describe(rs.status()) + ", so it can no longer be changed.");
                }
                if (!author) {
                    yield new Refusal(true, "Only " + rs.createdByName() + ", who started this draft, can change it.");
                }
                if (action == Action.SUBMIT && sameAsBase(rs)) {
                    yield new Refusal(false, "This draft makes no changes to version " + rs.basedOn()
                            + " yet. Change a rule before sending it for approval.");
                }
                yield null;
            }
            case WITHDRAW -> {
                if (rs.status() != RuleSetStatus.PENDING) {
                    yield new Refusal(false, "Only a version waiting for approval can be taken back.");
                }
                if (!author) {
                    yield new Refusal(true, "Only " + rs.createdByName() + ", who sent it for approval, can take it back.");
                }
                yield null;
            }
            case APPROVE, REJECT -> {
                if (rs.status() != RuleSetStatus.PENDING) {
                    yield new Refusal(false, "Only a version waiting for approval can be approved or rejected.");
                }
                if (!actor.admin()) {
                    yield new Refusal(true, "Only an admin can approve or reject a change to the rules.");
                }
                if (author) {
                    yield new Refusal(true, "You wrote version " + v + ", so another admin has to review it. One person"
                            + " cannot both make a change and approve it (maker-checker).");
                }
                if (action == Action.APPROVE && !Objects.equals(rs.basedOn(), live)) {
                    yield new Refusal(false, "Version " + v + " changes version " + rs.basedOn() + ", but version " + live
                            + " is live now. Publishing it would quietly undo whatever changed in between. Start a new"
                            + " draft from version " + live + " and make the change there.");
                }
                yield null;
            }
            case ACTIVATE -> {
                if (rs.status() != RuleSetStatus.PUBLISHED) {
                    yield new Refusal(false, "Only a version that has been published can be made live.");
                }
                if (!actor.admin()) {
                    yield new Refusal(true, "Only an admin can change which version is live.");
                }
                if (v == live) {
                    yield new Refusal(false, "Version " + v + " is already live.");
                }
                yield null;
            }
        };
    }

    /** True when a draft's rules are exactly those of the version it is based on. */
    private boolean sameAsBase(StoredRuleSet draft) {
        return draft.basedOn() != null
                && store.find(draft.basedOn()).map(base -> base.rules().equals(draft.rules())).orElse(false);
    }

    // ---- helpers ----------------------------------------------------------------------

    private void audit(Actor actor, AuditAction action, int version, String summary, Map<String, Object> details,
                       Instant at) {
        audit.record(actor, action, AuditLog.RULE_SET, String.valueOf(version), summary, details, at);
    }

    private static Map<String, Object> details(Integer replaced, String note) {
        Map<String, Object> details = new LinkedHashMap<>();
        if (replaced != null) {
            details.put("replaced", replaced);
        }
        if (note != null) {
            details.put("note", note);
        }
        return details;
    }

    private static RuleSetRefusedException changedMeanwhile(int version) {
        return new RuleSetRefusedException(false, "Version " + version + " was changed from somewhere else (another tab,"
                + " or another person) since you opened it. Reload to see the latest, then try again.");
    }

    static String describe(RuleSetStatus status) {
        return switch (status) {
            case DRAFT -> "a draft";
            case PENDING -> "waiting for approval";
            case PUBLISHED -> "published";
            case REJECTED -> "rejected";
            case DISCARDED -> "discarded";
        };
    }

    private static String cleanTitle(String title) {
        String name = title == null ? "" : title.strip();
        if (name.isEmpty()) {
            throw new InvalidRuleSetException(List.of("title: say in a few words what this version changes"));
        }
        if (name.length() > MAX_TITLE_LENGTH) {
            throw new InvalidRuleSetException(List.of("title: at most " + MAX_TITLE_LENGTH + " characters, got " + name.length()));
        }
        return name;
    }

    /** A reviewer's note: optional, trimmed, and not too long. */
    private static String cleanNote(String note) {
        String text = note == null ? "" : note.strip();
        if (text.length() > MAX_NOTE_LENGTH) {
            throw new InvalidRuleSetException(List.of("note: at most " + MAX_NOTE_LENGTH + " characters, got " + text.length()));
        }
        return text.isEmpty() ? null : text;
    }
}
