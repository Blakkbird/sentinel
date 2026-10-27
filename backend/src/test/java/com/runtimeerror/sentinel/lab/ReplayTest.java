package com.runtimeerror.sentinel.lab;

import com.runtimeerror.sentinel.engine.Condition;
import com.runtimeerror.sentinel.engine.DecisionEngine;
import com.runtimeerror.sentinel.engine.FeatureCatalogue;
import com.runtimeerror.sentinel.engine.FeatureCatalogue.FeatureSpec;
import com.runtimeerror.sentinel.engine.FeatureVector;
import com.runtimeerror.sentinel.engine.Outcome;
import com.runtimeerror.sentinel.engine.RuleDefinition;
import com.runtimeerror.sentinel.engine.RuleMode;
import com.runtimeerror.sentinel.engine.RuleSet;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.runtimeerror.sentinel.engine.Outcome.APPROVE;
import static com.runtimeerror.sentinel.engine.Outcome.DECLINE;
import static org.assertj.core.api.Assertions.assertThat;

/** Replay on its own: hand-made decisions, two small rule sets, no database. */
class ReplayTest {

    private static final FeatureCatalogue CATALOGUE = FeatureCatalogue.of(
            new FeatureSpec("count", "attempts this minute"),
            new FeatureSpec("device", "a fact only some decisions have"));

    private final DecisionEngine engine = new DecisionEngine();

    // Live (version 7) declines above 5 attempts; the version tried (8) declines above 3.
    private final RuleSet live = ruleSet(7, rule("FAST", Condition.gt("count", 5)));
    private final RuleSet tighter = ruleSet(8, rule("FAST", Condition.gt("count", 3)),
            new RuleDefinition("ODD_DEVICE", "needs a fact", Condition.gt("device", 0), DECLINE, "ODD_DEVICE", RuleMode.SHADOW));

    @Test
    void comparesTheTwoRuleSetsOnTheSameFacts() {
        ReplayReport report = Replay.run(history(1, 4, 5, 6), live, tighter, this::versions, engine, 50);

        assertThat(report.decisions()).isEqualTo(4);
        assertThat(report.changed()).isEqualTo(2);                       // the 4th and 5th attempts
        assertThat(report.moves()).containsExactly(new ReplayReport.Move(APPROVE, DECLINE, 2));
        assertThat(report.liveStrip()).isEqualTo("AAAD");               // oldest first
        assertThat(report.versionStrip()).isEqualTo("ADDD");
        assertThat(report.liveOutcomes()).containsEntry(DECLINE, 1);
        assertThat(report.versionOutcomes()).containsEntry(DECLINE, 3);
    }

    @Test
    void listsTheMostRecentChangesFirst() {
        ReplayReport report = Replay.run(history(1, 4, 5, 6), live, tighter, this::versions, engine, 50);

        assertThat(report.examples()).extracting(ReplayReport.ChangedDecision::transactionId)
                .containsExactly("txn-2", "txn-1");
        assertThat(report.examples().get(0).reasonCodes()).containsExactly("FAST");
    }

    @Test
    void countsRulesThatFireAndRulesThatCannotRun() {
        ReplayReport report = Replay.run(history(1, 4, 5, 6), live, tighter, this::versions, engine, 50);

        ReplayReport.RuleImpact fast = report.rules().get(0);
        assertThat(fast.liveFired()).isEqualTo(1);
        assertThat(fast.versionFired()).isEqualTo(3);

        ReplayReport.RuleImpact odd = report.rules().get(1);
        assertThat(odd.liveFired()).isNull();                            // a new rule
        assertThat(odd.versionAbstained()).isEqualTo(4);                 // no decision recorded "device"
    }

    @Test
    void checksThatEachDecisionsOwnRulesReproduceIt() {
        List<Replay.Recorded> decisions = new ArrayList<>(history(2, 9));
        decisions.add(recorded("txn-x", 1, APPROVE, 99));               // made by a version nobody has

        ReplayReport report = Replay.run(decisions, live, tighter, this::versions, engine, 50);

        assertThat(report.check()).isEqualTo(new ReplayReport.Check(2, 2, 1));
    }

    @Test
    void anEmptyHistoryIsAnEmptyReport() {
        ReplayReport report = Replay.run(List.of(), live, tighter, this::versions, engine, 50);

        assertThat(report.decisions()).isZero();
        assertThat(report.from()).isNull();
        assertThat(report.versionStrip()).isEmpty();
    }

    // ---- helpers ----------------------------------------------------------------------

    /** Decisions made by the live rules (version 7), with these attempt counts, oldest first. */
    private List<Replay.Recorded> history(int... counts) {
        List<Replay.Recorded> out = new ArrayList<>();
        for (int i = 0; i < counts.length; i++) {
            Outcome recorded = counts[i] > 5 ? DECLINE : APPROVE;
            out.add(recorded("txn-" + i, counts[i], recorded, 7));
        }
        return out;
    }

    private static Replay.Recorded recorded(String txn, int count, Outcome outcome, int version) {
        return new Replay.Recorded(txn, Instant.parse("2026-10-27T10:00:00Z"), "card-1", 49_00, "INR",
                outcome, outcome, version, FeatureVector.builder().put("count", count).build());
    }

    private Optional<RuleSet> versions(int version) {
        return version == 7 ? Optional.of(live) : version == 8 ? Optional.of(tighter) : Optional.empty();
    }

    private static RuleDefinition rule(String code, Condition condition) {
        return new RuleDefinition(code, "test rule", condition, DECLINE, code, RuleMode.ACTIVE);
    }

    private static RuleSet ruleSet(int version, RuleDefinition... rules) {
        return RuleSet.compile(version, List.of(rules), CATALOGUE);
    }
}
