package com.runtimeerror.sentinel.api;

import com.runtimeerror.sentinel.engine.Outcome;
import com.runtimeerror.sentinel.engine.RuleDefinition;
import com.runtimeerror.sentinel.engine.RuleMode;
import com.runtimeerror.sentinel.engine.RuleSet;

import java.util.List;

/**
 * The rule set in force, as readable JSON, for GET /v1/rules/active.
 * Conditions are shown in their human form, e.g. "card.txnCount.1m > 5".
 */
public record RuleSetView(int version, List<RuleView> rules) {

    public record RuleView(
            String code,
            String description,
            String condition,
            Outcome proposes,
            String reasonCode,
            RuleMode mode
    ) {
    }

    public static RuleSetView from(RuleSet ruleSet) {
        List<RuleView> rules = ruleSet.rules().stream()
                .map(RuleSet.CompiledRule::definition)
                .map(RuleSetView::view)
                .toList();
        return new RuleSetView(ruleSet.version(), rules);
    }

    private static RuleView view(RuleDefinition d) {
        return new RuleView(d.code(), d.description(), d.condition().describe(),
                d.proposes(), d.reasonCode(), d.mode());
    }
}
