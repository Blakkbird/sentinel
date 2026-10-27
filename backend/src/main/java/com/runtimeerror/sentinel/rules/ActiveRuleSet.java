package com.runtimeerror.sentinel.rules;

import com.runtimeerror.sentinel.engine.InvalidRuleSetException;
import com.runtimeerror.sentinel.engine.RuleSet;
import com.runtimeerror.sentinel.features.Features;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Holds the rule set currently in force.
 *
 * The rule set sits behind an AtomicReference, which is a box whose contents can be
 * swapped in one indivisible step. Each decision reads the box exactly once and then
 * uses what it got for the whole evaluation. So when a new version is published:
 *
 *  - no decision ever sees a mix of old and new rules
 *  - nothing has to be locked, and no decision ever waits for a publish
 *  - decisions already in flight simply finish on the version they started with
 *
 * From Sprint 2 the rules come from the database. At startup this loads whichever version
 * rule_set_activation says is live and compiles it, so an invalid live rule set stops the
 * application from starting at all. Failing loudly at boot is much better than failing
 * quietly at 2am.
 *
 * It also keeps every published version it has been asked about, compiled. Published
 * versions never change, so each is compiled once and kept. That is what lets the console
 * explain any old decision with the exact rules that made it.
 */
@Component
public class ActiveRuleSet {

    private static final Logger log = LoggerFactory.getLogger(ActiveRuleSet.class);

    private final RuleSetStore store;
    private final Map<Integer, RuleSet> published = new ConcurrentHashMap<>();
    private final AtomicReference<RuleSet> current;

    public ActiveRuleSet(RuleSetStore store) {
        this.store = store;
        RuleSetActivation live = store.latestActivation().orElseThrow(() -> new IllegalStateException(
                "No rule set is live. Migration V3 makes version 2 live, so the database has not been"
                        + " migrated properly."));
        RuleSet ruleSet = find(live.version()).orElseThrow(() -> new IllegalStateException(
                "The live rule set, version " + live.version() + ", is missing, not published, or does not compile."
                        + " See the warning above."));
        this.current = new AtomicReference<>(ruleSet);
        log.info("Rule set version {} is live ({} rules)", ruleSet.version(), ruleSet.rules().size());
    }

    public RuleSet current() {
        return current.get();
    }

    /**
     * The compiled rule set with this version number, if that version has been published.
     * Drafts are never returned: they can still change, so they are compiled fresh by
     * whoever needs one (the Rule Lab) instead of being cached here.
     */
    public Optional<RuleSet> find(int version) {
        RuleSet known = published.get(version);
        if (known != null) {
            return Optional.of(known);
        }
        Optional<StoredRuleSet> stored = store.find(version)
                .filter(ruleSet -> ruleSet.status() == RuleSetStatus.PUBLISHED);
        if (stored.isEmpty()) {
            return Optional.empty();
        }
        try {
            RuleSet compiled = RuleSet.compile(version, stored.get().rules(), Features.CATALOGUE);
            // If two threads compile the same version at once, both results are identical;
            // putIfAbsent keeps the first and everyone uses that one.
            RuleSet kept = published.putIfAbsent(version, compiled);
            return Optional.of(kept == null ? compiled : kept);
        } catch (InvalidRuleSetException e) {
            // Possible only if a feature was removed from the catalogue after this version was
            // published. The version stays in the database; we just cannot run it any more.
            log.warn("Rule set version {} no longer compiles against the feature catalogue: {}", version, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Makes a compiled rule set the live one. Called by the publish and rollback steps, after
     * the database has recorded the change.
     */
    public void activate(RuleSet ruleSet) {
        published.putIfAbsent(ruleSet.version(), ruleSet);
        current.set(ruleSet);
        log.info("Rule set version {} is now live", ruleSet.version());
    }
}
