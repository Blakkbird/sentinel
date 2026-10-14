package com.runtimeerror.sentinel.engine;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static com.runtimeerror.sentinel.engine.RuleEvaluation.Result.ABSTAINED;
import static com.runtimeerror.sentinel.engine.RuleEvaluation.Result.FIRED;
import static com.runtimeerror.sentinel.engine.RuleEvaluation.Result.NOT_FIRED;

/**
 * Turns a rule set and a feature vector into a verdict.
 *
 * This is a pure function in the mathematical sense: the same two inputs always give
 * the same output, and calling it changes nothing anywhere. It reads no clock, opens no
 * connection, and keeps no state between calls. Three things follow from that:
 *
 *  - it is safe to call from many threads at once, with no locking
 *  - replaying a stored decision reproduces it exactly, which is what makes audit and
 *    backtesting trustworthy
 *  - its cost is small and predictable, because all slow work (Redis, the database)
 *    has already happened before it is called
 */
public final class DecisionEngine {

    public Verdict evaluate(RuleSet ruleSet, FeatureVector features) {
        Outcome outcome = Outcome.APPROVE;
        Outcome shadowOutcome = Outcome.APPROVE;
        Set<String> reasons = new LinkedHashSet<>();          // keeps rule order, drops repeats
        List<RuleEvaluation> trace = new ArrayList<>(ruleSet.rules().size());

        for (RuleSet.CompiledRule rule : ruleSet.rules()) {
            RuleDefinition def = rule.definition();
            if (def.mode() == RuleMode.OFF) {
                continue;
            }

            RuleEvaluation.Result result;
            if (!features.hasAll(rule.requiredFeatures())) {
                result = ABSTAINED;
            } else if (def.condition().test(features)) {
                result = FIRED;
            } else {
                result = NOT_FIRED;
            }

            trace.add(new RuleEvaluation(def.code(), def.mode(), result, def.proposes(), def.reasonCode()));

            if (result == FIRED) {
                shadowOutcome = shadowOutcome.mostSevere(def.proposes());
                if (def.mode() == RuleMode.ACTIVE) {
                    outcome = outcome.mostSevere(def.proposes());
                    reasons.add(def.reasonCode());
                }
            }
        }

        return new Verdict(outcome, List.copyOf(reasons), shadowOutcome, trace, ruleSet.version());
    }
}
