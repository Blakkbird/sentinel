package com.runtimeerror.sentinel.engine;

import com.runtimeerror.sentinel.engine.FeatureCatalogue.FeatureSpec;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.runtimeerror.sentinel.engine.Condition.gt;
import static com.runtimeerror.sentinel.engine.Outcome.APPROVE;
import static com.runtimeerror.sentinel.engine.Outcome.DECLINE;
import static com.runtimeerror.sentinel.engine.Outcome.STEP_UP;
import static com.runtimeerror.sentinel.engine.RuleEvaluation.Result.ABSTAINED;
import static com.runtimeerror.sentinel.engine.RuleEvaluation.Result.FIRED;
import static com.runtimeerror.sentinel.engine.RuleEvaluation.Result.NOT_FIRED;
import static com.runtimeerror.sentinel.engine.RuleMode.ACTIVE;
import static com.runtimeerror.sentinel.engine.RuleMode.OFF;
import static com.runtimeerror.sentinel.engine.RuleMode.SHADOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class DecisionEngineTest {

    private static final FeatureCatalogue CATALOGUE = FeatureCatalogue.of(
            new FeatureSpec("amount", "test amount"),
            new FeatureSpec("count", "test count"));

    private final DecisionEngine engine = new DecisionEngine();

    private static RuleDefinition rule(String code, Condition condition, Outcome proposes, RuleMode mode) {
        return new RuleDefinition(code, "test rule " + code, condition, proposes, code + "_REASON", mode);
    }

    private static RuleSet ruleSet(RuleDefinition... definitions) {
        return RuleSet.compile(7, List.of(definitions), CATALOGUE);
    }

    private static FeatureVector features(double amount, double count) {
        return FeatureVector.builder().put("amount", amount).put("count", count).build();
    }

    @Test
    void approvesWhenNothingFires() {
        Verdict verdict = engine.evaluate(
                ruleSet(rule("BIG", gt("amount", 1000), STEP_UP, ACTIVE)),
                features(10, 1));

        assertThat(verdict.outcome()).isEqualTo(APPROVE);
        assertThat(verdict.reasonCodes()).isEmpty();
        assertThat(verdict.ruleSetVersion()).isEqualTo(7);
    }

    @Test
    void theMostSevereActiveProposalWins() {
        Verdict verdict = engine.evaluate(
                ruleSet(
                        rule("BIG", gt("amount", 100), STEP_UP, ACTIVE),
                        rule("FAST", gt("count", 3), DECLINE, ACTIVE)),
                features(500, 9));

        assertThat(verdict.outcome()).isEqualTo(DECLINE);
        assertThat(verdict.reasonCodes()).containsExactly("BIG_REASON", "FAST_REASON");
    }

    @Test
    void shadowRulesAreRecordedButCannotChangeTheOutcome() {
        Verdict verdict = engine.evaluate(
                ruleSet(
                        rule("LIVE", gt("count", 5), DECLINE, ACTIVE),
                        rule("CANDIDATE", gt("count", 3), DECLINE, SHADOW)),
                features(10, 4));

        assertThat(verdict.outcome()).isEqualTo(APPROVE);
        assertThat(verdict.shadowOutcome()).isEqualTo(DECLINE);
        assertThat(verdict.shadowWouldChange()).isTrue();
        assertThat(verdict.reasonCodes()).isEmpty();
        assertThat(verdict.trace())
                .extracting(RuleEvaluation::ruleCode, RuleEvaluation::result)
                .containsExactly(
                        tuple("LIVE", NOT_FIRED),
                        tuple("CANDIDATE", FIRED));
    }

    @Test
    void aRuleAbstainsWhenAFeatureItNeedsIsMissing() {
        FeatureVector noCount = FeatureVector.builder().put("amount", 10).build();

        Verdict verdict = engine.evaluate(
                ruleSet(rule("FAST", gt("count", 3), DECLINE, ACTIVE)),
                noCount);

        assertThat(verdict.outcome()).isEqualTo(APPROVE);
        assertThat(verdict.trace().get(0).result()).isEqualTo(ABSTAINED);
    }

    @Test
    void offRulesAreSkippedEntirely() {
        Verdict verdict = engine.evaluate(
                ruleSet(
                        rule("RETIRED", gt("amount", 0), DECLINE, OFF),
                        rule("BIG", gt("amount", 100), STEP_UP, ACTIVE)),
                features(500, 1));

        assertThat(verdict.outcome()).isEqualTo(STEP_UP);
        assertThat(verdict.trace()).extracting(RuleEvaluation::ruleCode).containsExactly("BIG");
    }

    @Test
    void theSameInputsAlwaysGiveTheSameVerdict() {
        RuleSet rules = ruleSet(
                rule("BIG", gt("amount", 100), STEP_UP, ACTIVE),
                rule("FAST", gt("count", 3), DECLINE, SHADOW));
        FeatureVector input = features(500, 4);

        // This property is what makes replay and audit trustworthy.
        assertThat(engine.evaluate(rules, input)).isEqualTo(engine.evaluate(rules, input));
    }
}
