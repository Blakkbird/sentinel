package com.runtimeerror.sentinel.rules;

import com.runtimeerror.sentinel.engine.RuleSet;
import com.runtimeerror.sentinel.features.Features;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Holds the rule set currently in force.
 *
 * The rule set sits behind an AtomicReference, which is a box whose contents can be
 * swapped in one indivisible step. Each decision reads the box exactly once and then
 * uses what it got for the whole evaluation. So when a new version is activated in
 * stage 3:
 *
 *  - no decision ever sees a mix of old and new rules
 *  - nothing has to be locked, and no decision ever waits for a publish
 *  - decisions already in flight simply finish on the version they started with
 *
 * The starter rules are compiled at startup, which means an invalid rule set stops the
 * application from starting at all. Failing loudly at boot is much better than failing
 * quietly at 2am.
 */
@Component
public class ActiveRuleSet {

    private final AtomicReference<RuleSet> current;

    public ActiveRuleSet() {
        RuleSet starter = RuleSet.compile(StarterRules.VERSION, StarterRules.definitions(), Features.CATALOGUE);
        this.current = new AtomicReference<>(starter);
    }

    public RuleSet current() {
        return current.get();
    }

    /** Stage 3: called after a publish or a rollback. */
    public void activate(RuleSet ruleSet) {
        current.set(ruleSet);
    }
}
