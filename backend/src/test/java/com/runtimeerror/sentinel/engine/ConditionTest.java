package com.runtimeerror.sentinel.engine;

import org.junit.jupiter.api.Test;

import static com.runtimeerror.sentinel.engine.Condition.all;
import static com.runtimeerror.sentinel.engine.Condition.any;
import static com.runtimeerror.sentinel.engine.Condition.eq;
import static com.runtimeerror.sentinel.engine.Condition.gt;
import static com.runtimeerror.sentinel.engine.Condition.gte;
import static com.runtimeerror.sentinel.engine.Condition.lt;
import static com.runtimeerror.sentinel.engine.Condition.lte;
import static com.runtimeerror.sentinel.engine.Condition.neq;
import static com.runtimeerror.sentinel.engine.Condition.not;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Notice what is missing from every test in this package: no Spring, no database, no
 * Redis, no mocks. That is the payoff of keeping the engine pure. These run in
 * milliseconds, so you can afford hundreds of them, which means you will write them.
 */
class ConditionTest {

    private final FeatureVector features = FeatureVector.builder()
            .put("a", 5)
            .put("b", 0)
            .build();

    @Test
    void comparisonsApplyTheirOperator() {
        assertThat(gt("a", 4).test(features)).isTrue();
        assertThat(gt("a", 5).test(features)).isFalse();
        assertThat(gte("a", 5).test(features)).isTrue();
        assertThat(lt("a", 6).test(features)).isTrue();
        assertThat(lte("a", 4).test(features)).isFalse();
        assertThat(eq("b", 0).test(features)).isTrue();
        assertThat(neq("b", 0).test(features)).isFalse();
    }

    @Test
    void allAnyAndNotCombineConditions() {
        assertThat(all(gt("a", 1), eq("b", 0)).test(features)).isTrue();
        assertThat(all(gt("a", 1), eq("b", 1)).test(features)).isFalse();
        assertThat(any(gt("a", 100), eq("b", 0)).test(features)).isTrue();
        assertThat(any(gt("a", 100), eq("b", 1)).test(features)).isFalse();
        assertThat(not(eq("b", 0)).test(features)).isFalse();
    }

    @Test
    void collectsEveryFeatureItReads() {
        Condition condition = all(gt("a", 1), any(eq("b", 0), not(lt("c", 3))));
        assertThat(condition.features()).containsExactly("a", "b", "c");
    }

    @Test
    void describesItselfReadably() {
        Condition condition = all(gt("card.txnCount.1m", 5), lte("txn.amountMinor", 100.5));
        assertThat(condition.describe())
                .isEqualTo("all(card.txnCount.1m > 5, txn.amountMinor <= 100.5)");
    }

    @Test
    void rejectsNonFiniteValues() {
        assertThatThrownBy(() -> gt("a", Double.NaN)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> FeatureVector.builder().put("x", Double.POSITIVE_INFINITY))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void operatorsRoundTripThroughTheirSymbols() {
        for (Operator op : Operator.values()) {
            assertThat(Operator.fromSymbol(op.symbol())).isEqualTo(op);
        }
    }
}
