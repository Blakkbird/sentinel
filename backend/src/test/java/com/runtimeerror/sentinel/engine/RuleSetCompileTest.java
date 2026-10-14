package com.runtimeerror.sentinel.engine;

import com.runtimeerror.sentinel.engine.FeatureCatalogue.FeatureSpec;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.runtimeerror.sentinel.engine.Condition.all;
import static com.runtimeerror.sentinel.engine.Condition.gt;
import static com.runtimeerror.sentinel.engine.Outcome.APPROVE;
import static com.runtimeerror.sentinel.engine.Outcome.DECLINE;
import static com.runtimeerror.sentinel.engine.RuleMode.ACTIVE;
import static com.runtimeerror.sentinel.engine.RuleMode.OFF;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RuleSetCompileTest {

    private static final FeatureCatalogue CATALOGUE = FeatureCatalogue.of(
            new FeatureSpec("amount", "test amount"),
            new FeatureSpec("count", "test count"));

    @Test
    void aTypoInAFeatureNameIsCaughtAtCompileTime() {
        RuleDefinition typo = new RuleDefinition("FAST", "typo", gt("cuont", 3), DECLINE, "FAST", ACTIVE);

        assertThatThrownBy(() -> RuleSet.compile(1, List.of(typo), CATALOGUE))
                .isInstanceOf(InvalidRuleSetException.class)
                .hasMessageContaining("FAST: unknown feature 'cuont'");
    }

    @Test
    void everyProblemIsReportedAtOnce() {
        List<RuleDefinition> broken = List.of(
                new RuleDefinition("lower_case", "bad code", gt("amount", 1), DECLINE, "OK", ACTIVE),
                new RuleDefinition("DUP", "first", gt("amount", 1), DECLINE, "OK", ACTIVE),
                new RuleDefinition("DUP", "second", gt("nope", 1), APPROVE, "OK", ACTIVE));

        assertThatThrownBy(() -> RuleSet.compile(1, broken, CATALOGUE))
                .isInstanceOfSatisfying(InvalidRuleSetException.class, e -> assertThat(e.problems())
                        .anyMatch(p -> p.contains("UPPER_SNAKE_CASE"))
                        .anyMatch(p -> p.contains("duplicate rule code"))
                        .anyMatch(p -> p.contains("proposes APPROVE"))
                        .anyMatch(p -> p.contains("unknown feature 'nope'")));
    }

    @Test
    void requiredFeaturesIgnoreRulesThatAreOff() {
        RuleSet rules = RuleSet.compile(1, List.of(
                new RuleDefinition("ON", "on", gt("amount", 1), DECLINE, "ON", ACTIVE),
                new RuleDefinition("RETIRED", "off", all(gt("count", 1)), DECLINE, "RETIRED", OFF)),
                CATALOGUE);

        assertThat(rules.requiredFeatures()).containsExactly("amount");
    }
}
