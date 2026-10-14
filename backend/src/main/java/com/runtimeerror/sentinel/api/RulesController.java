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
 * Small, but it makes the system inspectable from Swagger UI on day one, and the
 * console's rule screens (stage 5) are built on exactly these two responses.
 */
@RestController
public class RulesController {

    private final ActiveRuleSet ruleSets;

    public RulesController(ActiveRuleSet ruleSets) {
        this.ruleSets = ruleSets;
    }

    @GetMapping("/v1/rules/active")
    public RuleSetView activeRules() {
        return RuleSetView.from(ruleSets.current());
    }

    @GetMapping("/v1/features")
    public List<FeatureSpec> features() {
        return List.copyOf(Features.CATALOGUE.all());
    }
}
