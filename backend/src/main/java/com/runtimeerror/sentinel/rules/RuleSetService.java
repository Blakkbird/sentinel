package com.runtimeerror.sentinel.rules;

import com.runtimeerror.sentinel.engine.InvalidRuleSetException;
import com.runtimeerror.sentinel.engine.RuleDefinition;
import com.runtimeerror.sentinel.engine.RuleSet;
import com.runtimeerror.sentinel.features.Features;
import com.runtimeerror.sentinel.security.Actor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;

/**
 * The rule workflow: who may do what to which version, and doing it.
 *
 * Every "may this person do this, right now?" question is answered in one method, refusal().
 * The console asks the same method which buttons to show (permissions()), so a button the
 * console offers is always one the server will accept, and the reason a button is missing is
 * the same sentence the server would send back.
 *
 * Nothing invalid is ever stored: a draft's rules are read with RuleSetJson (shape) and checked
 * with RuleSet.compile (meaning) before every save. So every row in rule_set compiles.
 */
@Service
public class RuleSetService {

    /** Things a person can do to a version. The console shows a button for each one allowed. */
    public enum Action {
        EDIT,
        DISCARD
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

    private final RuleSetStore store;
    private final Clock clock;

    public RuleSetService(RuleSetStore store, Clock clock) {
        this.store = store;
        this.clock = clock;
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
        int version = store.insertDraft(live, name, source.rules(), author, clock.instant());
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
            throw new RuleSetRefusedException(false, "Version " + version + " was saved from somewhere else (another tab,"
                    + " or another person) since you opened it. Reload to see the latest, then make your change again.");
        }
        return get(version);
    }

    public StoredRuleSet discard(int version, Actor author) {
        StoredRuleSet draft = get(version);
        require(Action.DISCARD, draft, author);
        if (!store.discard(version, clock.instant())) {
            throw new RuleSetRefusedException(false, "Version " + version + " is no longer a draft. Reload to see why.");
        }
        return get(version);
    }

    // ---- who may do what --------------------------------------------------------------

    /** Every action, and whether this person may take it on this version now. */
    public List<Permission> permissions(StoredRuleSet ruleSet, Actor actor) {
        List<Permission> permissions = new ArrayList<>();
        for (Action action : Action.values()) {
            Refusal refusal = refusal(action, ruleSet, actor);
            permissions.add(new Permission(action, refusal == null, refusal == null ? null : refusal.reason()));
        }
        return permissions;
    }

    private void require(Action action, StoredRuleSet ruleSet, Actor actor) {
        Refusal refusal = refusal(action, ruleSet, actor);
        if (refusal != null) {
            throw refusal.exception();
        }
    }

    /** Why this person may not take this action on this version right now, or null if they may. */
    private Refusal refusal(Action action, StoredRuleSet rs, Actor actor) {
        boolean author = rs.createdBy().equals(actor.username());
        return switch (action) {
            case EDIT, DISCARD -> {
                if (rs.status() != RuleSetStatus.DRAFT) {
                    yield new Refusal(false, "Version " + rs.version() + " is " + describe(rs.status())
                            + ", so it can no longer be changed.");
                }
                if (!author) {
                    yield new Refusal(true, "Only " + rs.createdByName() + ", who started this draft, can change it.");
                }
                yield null;
            }
        };
    }

    // ---- helpers ----------------------------------------------------------------------

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
}
