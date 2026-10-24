package com.runtimeerror.sentinel.console;

import com.runtimeerror.sentinel.decision.StoredDecision;
import com.runtimeerror.sentinel.engine.Explainer;
import com.runtimeerror.sentinel.engine.FeatureCatalogue;
import com.runtimeerror.sentinel.engine.FeatureVector;
import com.runtimeerror.sentinel.engine.RuleDefinition;
import com.runtimeerror.sentinel.engine.RuleEvaluation;
import com.runtimeerror.sentinel.engine.RuleSet;
import com.runtimeerror.sentinel.features.Features;
import com.runtimeerror.sentinel.rules.ActiveRuleSet;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Builds the decision page's view from a stored decision.
 *
 * This is where "every decision stores its facts" pays off. The evidence for each rule is
 * worked out again here, from the stored facts and the rule's definition, and it always
 * matches what happened at the time, because the engine is pure and the facts never change.
 */
@Component
public class DecisionViews {

    private final ActiveRuleSet ruleSets;

    public DecisionViews(ActiveRuleSet ruleSets) {
        this.ruleSets = ruleSets;
    }

    public DecisionView of(StoredDecision d) {
        // Prefer the exact version that made the decision. If this server no longer has it,
        // fall back to the current version and say so (definitionVersion).
        RuleSet exact = ruleSets.find(d.ruleSetVersion()).orElse(null);
        RuleSet current = ruleSets.current();

        List<DecisionView.RuleResult> rules = new ArrayList<>(d.trace().size());
        for (RuleEvaluation step : d.trace()) {
            RuleSet source = exact;
            RuleDefinition definition = definition(exact, step.ruleCode());
            if (definition == null) {
                source = current;
                definition = definition(current, step.ruleCode());
            }
            rules.add(new DecisionView.RuleResult(
                    step.ruleCode(), step.mode(), step.result(), step.proposes(), step.reasonCode(),
                    definition == null ? null : definition.description(),
                    definition == null ? null : definition.condition().describe(),
                    definition == null ? null : source.version(),
                    definition == null ? List.of() : Explainer.evidence(definition.condition(), d.features())));
        }

        return new DecisionView(d.decisionId(), d.transactionId(), d.cardId(), d.deviceId(), d.merchantId(),
                d.amountMinor(), d.currency(), d.occurredAt(), d.decidedAt(), d.outcome(), d.shadowOutcome(),
                d.reasonCodes(), d.ruleSetVersion(), d.degraded(), d.computeMicros(),
                facts(d.features()), List.copyOf(rules));
    }

    private static RuleDefinition definition(RuleSet ruleSet, String code) {
        if (ruleSet == null) {
            return null;
        }
        for (RuleSet.CompiledRule rule : ruleSet.rules()) {
            if (rule.definition().code().equals(code)) {
                return rule.definition();
            }
        }
        return null;
    }

    /** Facts in catalogue order (the payment amount first), then any the catalogue no longer lists. */
    private static List<DecisionView.Fact> facts(FeatureVector features) {
        Map<String, Double> values = features.asMap();
        List<DecisionView.Fact> facts = new ArrayList<>(values.size());
        Set<String> listed = new HashSet<>();
        for (FeatureCatalogue.FeatureSpec spec : Features.CATALOGUE.all()) {
            Double value = values.get(spec.name());
            if (value != null) {
                facts.add(new DecisionView.Fact(spec.name(), value, spec.description()));
                listed.add(spec.name());
            }
        }
        values.forEach((name, value) -> {
            if (!listed.contains(name)) {
                facts.add(new DecisionView.Fact(name, value, null));
            }
        });
        return List.copyOf(facts);
    }
}
