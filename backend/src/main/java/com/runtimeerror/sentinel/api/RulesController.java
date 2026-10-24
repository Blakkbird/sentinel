package com.runtimeerror.sentinel.api;

import com.runtimeerror.sentinel.engine.FeatureCatalogue.FeatureSpec;
import com.runtimeerror.sentinel.features.Features;
import com.runtimeerror.sentinel.rules.ActiveRuleSet;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Read-only views of what the engine is working with.
 *
 *   GET /v1/rules/active   the rule set in force: version, every rule, its mode
 *   GET /v1/features       every feature a rule may use, with a description
 *
 * Each is also served under /api, the prefix for everything the web console calls.
 * Same data, two front doors: /v1 is for payment systems (API keys, from Sprint 1),
 * /api is for people signed in to the console. Keeping the prefixes apart lets each
 * one have its own security rules.
 */
@RestController
public class RulesController {

    private final ActiveRuleSet ruleSets;

    public RulesController(ActiveRuleSet ruleSets) {
        this.ruleSets = ruleSets;
    }

    @GetMapping({"/v1/rules/active", "/api/rules/active"})
    public RuleSetView activeRules() {
        return RuleSetView.from(ruleSets.current());
    }

    @GetMapping({"/v1/features", "/api/features"})
    public List<FeatureSpec> features() {
        return List.copyOf(Features.CATALOGUE.all());
    }
}
