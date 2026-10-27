package com.runtimeerror.sentinel.rules;

import com.runtimeerror.sentinel.engine.Condition;
import com.runtimeerror.sentinel.engine.InvalidRuleSetException;
import com.runtimeerror.sentinel.engine.Operator;
import com.runtimeerror.sentinel.engine.Outcome;
import com.runtimeerror.sentinel.engine.RuleDefinition;
import com.runtimeerror.sentinel.engine.RuleMode;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/** The stored JSON format. Plain Java maps in, plain Java maps out: no database, no Spring. */
class RuleSetJsonTest {

    @Test
    void theStarterRulesSurviveARoundTripExactly() {
        List<RuleDefinition> rules = StarterRules.definitions();

        // RuleDefinition and the Condition records compare field by field, so equals() here
        // means every code, threshold, mode and nested condition came back identical.
        assertThat(RuleSetJson.read(RuleSetJson.write(rules))).isEqualTo(rules);
    }

    @Test
    void nestedConditionsAndFractionsRoundTripToo() {
        RuleDefinition rule = rule(new Condition.Any(List.of(
                new Condition.Not(new Condition.Compare("txn.amountMinor", Operator.LT, 1.5)),
                new Condition.All(List.of(new Condition.Compare("card.txnCount.1m", Operator.GTE, 2))))));

        assertThat(RuleSetJson.read(RuleSetJson.write(List.of(rule)))).containsExactly(rule);
    }

    @Test
    void wholeNumbersAreWrittenTheWayPeopleWriteThem() {
        Map<String, Object> written = RuleSetJson.writeCondition(new Condition.Compare("card.txnCount.1m", Operator.GT, 5));

        assertThat(written).containsEntry("type", "compare").containsEntry("op", "GT").containsEntry("value", 5L);
    }

    @Test
    void anyKindOfJsonNumberIsAccepted() {
        Map<String, Object> rule = validRule();
        rule.put("condition", Map.of("type", "compare", "feature", "f", "op", "GT", "value", new BigDecimal("2.50")));

        Condition.Compare read = (Condition.Compare) RuleSetJson.readRules(List.of(rule)).get(0).condition();

        assertThat(read.value()).isEqualTo(2.5);
    }

    @Test
    void everyProblemIsReportedWithItsLocation() {
        Map<String, Object> broken = validRule();
        broken.put("mode", "ACTIV");
        broken.put("feautre", 1);
        broken.put("condition", Map.of("type", "all", "parts", List.of(
                Map.of("type", "compare", "feature", "f", "op", "GREATER", "value", 5),
                Map.of("type", "maybe"))));

        InvalidRuleSetException e = catchThrowableOfType(InvalidRuleSetException.class,
                () -> RuleSetJson.readRules(List.of(broken, "not a rule")));

        assertThat(e.problems()).containsExactly(
                "rules[0] (CODE).feautre: unknown field",
                "rules[0] (CODE).mode: must be one of OFF, SHADOW, ACTIVE, got 'ACTIV'",
                "rules[0] (CODE).condition.parts[0].op: must be one of GT, GTE, LT, LTE, EQ, NEQ, got 'GREATER'",
                "rules[0] (CODE).condition.parts[1].type: unknown type 'maybe', expected compare, all, any or not",
                "rules[1]: must be an object { ... }");
    }

    @Test
    void aDocumentMustSayWhichFormatItIs() {
        Map<String, Object> document = new LinkedHashMap<>(RuleSetJson.write(StarterRules.definitions()));
        document.put("formatVersion", 2);

        assertThatThrownBy(() -> RuleSetJson.read(document))
                .isInstanceOf(InvalidRuleSetException.class)
                .hasMessageContaining("formatVersion: must be 1");
    }

    @Test
    void conditionsCannotNestWithoutLimit() {
        Object condition = Map.of("type", "compare", "feature", "f", "op", "GT", "value", 1);
        for (int i = 0; i < 8; i++) {
            condition = Map.of("type", "not", "inner", condition);
        }
        Map<String, Object> rule = validRule();
        rule.put("condition", condition);

        assertThatThrownBy(() -> RuleSetJson.readRules(List.of(rule)))
                .isInstanceOf(InvalidRuleSetException.class)
                .hasMessageContaining("at most 6 levels deep");
    }

    private static RuleDefinition rule(Condition condition) {
        return new RuleDefinition("NESTED", "a test rule", condition, Outcome.DECLINE, "NESTED", RuleMode.SHADOW);
    }

    private static Map<String, Object> validRule() {
        Map<String, Object> rule = new LinkedHashMap<>();
        rule.put("code", "CODE");
        rule.put("description", "a test rule");
        rule.put("mode", "ACTIVE");
        rule.put("proposes", "DECLINE");
        rule.put("reasonCode", "REASON");
        rule.put("condition", Map.of("type", "compare", "feature", "f", "op", "GT", "value", 1));
        return rule;
    }
}
