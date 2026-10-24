package com.runtimeerror.sentinel.engine;

import org.junit.jupiter.api.Test;

import java.util.List;

import static com.runtimeerror.sentinel.engine.Condition.all;
import static com.runtimeerror.sentinel.engine.Condition.gt;
import static com.runtimeerror.sentinel.engine.Condition.not;
import static org.assertj.core.api.Assertions.assertThat;

class ExplainerTest {

    private static FeatureVector facts(double count, double amount) {
        return FeatureVector.builder().put("count", count).put("amount", amount).build();
    }

    @Test
    void listsEveryComparisonNextToTheValueSeen() {
        List<Evidence> evidence = Explainer.evidence(all(gt("count", 5), gt("amount", 1000)), facts(7, 500));

        assertThat(evidence).containsExactly(
                new Evidence("count", Operator.GT, 5, 7.0, true),
                new Evidence("amount", Operator.GT, 1000, 500.0, false));
    }

    @Test
    void aMissingFactHasNoValueAndNeverHolds() {
        FeatureVector onlyAmount = FeatureVector.builder().put("amount", 1).build();

        List<Evidence> evidence = Explainer.evidence(gt("count", 5), onlyAmount);

        assertThat(evidence).singleElement().satisfies(e -> {
            assertThat(e.actual()).isNull();
            assertThat(e.holds()).isFalse();
        });
    }

    @Test
    void looksInsideNot() {
        List<Evidence> evidence = Explainer.evidence(not(gt("count", 5)), facts(2, 0));

        assertThat(evidence).extracting(Evidence::feature).containsExactly("count");
    }

    @Test
    void whenTheEngineFiresARuleEveryPartOfAnAllHolds() {
        Condition condition = all(gt("count", 5), gt("amount", 1000));
        RuleSet rules = RuleSet.compile(1,
                List.of(new RuleDefinition("BOTH", "test", condition, Outcome.DECLINE, "BOTH", RuleMode.ACTIVE)),
                FeatureCatalogue.of(new FeatureCatalogue.FeatureSpec("count", "c"),
                        new FeatureCatalogue.FeatureSpec("amount", "a")));
        FeatureVector features = facts(9, 5000);

        Verdict verdict = new DecisionEngine().evaluate(rules, features);

        assertThat(verdict.outcome()).isEqualTo(Outcome.DECLINE);
        assertThat(Explainer.evidence(condition, features)).allMatch(Evidence::holds);
    }
}
