package com.runtimeerror.sentinel.rules;

import com.runtimeerror.sentinel.engine.RuleSet;
import com.runtimeerror.sentinel.features.Features;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StarterRulesTest {

    @Test
    void theStarterRulesCompileAgainstTheRealCatalogue() {
        RuleSet rules = RuleSet.compile(StarterRules.VERSION, StarterRules.definitions(), Features.CATALOGUE);

        assertThat(rules.rules()).hasSize(StarterRules.definitions().size());
        assertThat(Features.CATALOGUE.names()).containsAll(rules.requiredFeatures());
    }
}
