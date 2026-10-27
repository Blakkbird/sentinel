package com.runtimeerror.sentinel.lab;

import com.runtimeerror.sentinel.engine.DecisionEngine;
import com.runtimeerror.sentinel.engine.FeatureVector;
import com.runtimeerror.sentinel.engine.Outcome;
import com.runtimeerror.sentinel.engine.RuleDefinition;
import com.runtimeerror.sentinel.engine.RuleEvaluation;
import com.runtimeerror.sentinel.engine.RuleSet;
import com.runtimeerror.sentinel.engine.Verdict;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.IntFunction;

/**
 * Replays past decisions through two rule sets and compares the results. Pure: no database, no
 * Spring, no clock. Facts in, report out.
 *
 * This is where storing every decision's facts pays off a second time (the first was the
 * decision page's explanations). Because the facts are frozen, "what would version 3 have done
 * to last Tuesday's payments?" has an exact answer: run the engine on last Tuesday's facts.
 * No re-reading of Redis, whose counters have long since moved on.
 *
 * Shadow mode can only test rules that make decisions stricter, because the most severe
 * proposal always wins. Replay tests anything, including loosening a rule (raising a threshold),
 * which is decision D9.
 */
public final class Replay {

    /** One stored decision, reduced to what replay needs. */
    public record Recorded(String transactionId, Instant decidedAt, String cardId, long amountMinor, String currency,
                           Outcome outcome, Outcome shadowOutcome, int ruleSetVersion, FeatureVector features) {
    }

    private Replay() {
    }

    /**
     * @param decisions oldest first
     * @param live      the rule set in force now: the baseline
     * @param version   the rule set being tried
     * @param versions  finds the rule set that originally made a decision, for the check
     * @param examples  how many changed decisions to list (the most recent ones)
     */
    public static ReplayReport run(List<Recorded> decisions, RuleSet live, RuleSet version,
                                   IntFunction<Optional<RuleSet>> versions, DecisionEngine engine, int examples) {
        Map<Outcome, Integer> liveOutcomes = zeroes();
        Map<Outcome, Integer> versionOutcomes = zeroes();
        Map<Outcome, Map<Outcome, Integer>> moves = new EnumMap<>(Outcome.class);
        Map<String, Integer> liveFired = new HashMap<>();
        Map<String, Integer> versionFired = new HashMap<>();
        Map<String, Integer> versionAbstained = new HashMap<>();
        StringBuilder liveStrip = new StringBuilder(decisions.size());
        StringBuilder versionStrip = new StringBuilder(decisions.size());
        List<ReplayReport.ChangedDecision> changed = new ArrayList<>();
        int checked = 0;
        int matched = 0;
        int unknown = 0;

        for (Recorded d : decisions) {
            Verdict before = engine.evaluate(live, d.features());
            Verdict after = engine.evaluate(version, d.features());

            liveOutcomes.merge(before.outcome(), 1, Integer::sum);
            versionOutcomes.merge(after.outcome(), 1, Integer::sum);
            liveStrip.append(letter(before.outcome()));
            versionStrip.append(letter(after.outcome()));
            count(before.trace(), liveFired, null);
            count(after.trace(), versionFired, versionAbstained);

            if (before.outcome() != after.outcome()) {
                moves.computeIfAbsent(before.outcome(), o -> new EnumMap<>(Outcome.class))
                        .merge(after.outcome(), 1, Integer::sum);
                changed.add(new ReplayReport.ChangedDecision(d.transactionId(), d.decidedAt(), d.cardId(),
                        d.amountMinor(), d.currency(), before.outcome(), after.outcome(), after.reasonCodes()));
            }

            // The check: the rules that made this decision, run again on its facts, must give
            // back exactly what was recorded. If they ever do not, replay cannot be trusted, and
            // the console says so instead of quietly showing numbers.
            Optional<RuleSet> original = versions.apply(d.ruleSetVersion());
            if (original.isEmpty()) {
                unknown++;
            } else {
                checked++;
                Verdict again = engine.evaluate(original.get(), d.features());
                if (again.outcome() == d.outcome() && again.shadowOutcome() == d.shadowOutcome()) {
                    matched++;
                }
            }
        }

        List<ReplayReport.Move> moveList = new ArrayList<>();
        moves.forEach((from, to) -> to.forEach((outcome, n) -> moveList.add(new ReplayReport.Move(from, outcome, n))));
        moveList.sort((a, b) -> Integer.compare(b.count(), a.count()));

        List<ReplayReport.ChangedDecision> newestFirst = new ArrayList<>(changed.reversed());
        return new ReplayReport(
                version.version(),
                live.version(),
                decisions.size(),
                decisions.isEmpty() ? null : decisions.getFirst().decidedAt(),
                decisions.isEmpty() ? null : decisions.getLast().decidedAt(),
                liveOutcomes,
                versionOutcomes,
                changed.size(),
                moveList,
                impacts(live, version, liveFired, versionFired, versionAbstained),
                List.copyOf(newestFirst.subList(0, Math.min(examples, newestFirst.size()))),
                liveStrip.toString(),
                versionStrip.toString(),
                new ReplayReport.Check(checked, matched, unknown));
    }

    /** The version's rules in their order, then any rule only the live set has (removed by this version). */
    private static List<ReplayReport.RuleImpact> impacts(RuleSet live, RuleSet version, Map<String, Integer> liveFired,
                                                         Map<String, Integer> versionFired, Map<String, Integer> abstained) {
        Map<String, RuleDefinition> inLive = new LinkedHashMap<>();
        live.rules().forEach(rule -> inLive.put(rule.definition().code(), rule.definition()));

        List<ReplayReport.RuleImpact> impacts = new ArrayList<>();
        for (RuleSet.CompiledRule rule : version.rules()) {
            RuleDefinition d = rule.definition();
            impacts.add(new ReplayReport.RuleImpact(d.code(), d.mode(), d.proposes(),
                    inLive.containsKey(d.code()) ? liveFired.getOrDefault(d.code(), 0) : null,
                    versionFired.getOrDefault(d.code(), 0),
                    abstained.getOrDefault(d.code(), 0)));
            inLive.remove(d.code());
        }
        inLive.values().forEach(d -> impacts.add(new ReplayReport.RuleImpact(d.code(), d.mode(), d.proposes(),
                liveFired.getOrDefault(d.code(), 0), null, 0)));
        return impacts;
    }

    private static void count(List<RuleEvaluation> trace, Map<String, Integer> fired, Map<String, Integer> abstained) {
        for (RuleEvaluation step : trace) {
            if (step.result() == RuleEvaluation.Result.FIRED) {
                fired.merge(step.ruleCode(), 1, Integer::sum);
            } else if (abstained != null && step.result() == RuleEvaluation.Result.ABSTAINED) {
                abstained.merge(step.ruleCode(), 1, Integer::sum);
            }
        }
    }

    private static Map<Outcome, Integer> zeroes() {
        Map<Outcome, Integer> counts = new EnumMap<>(Outcome.class);
        for (Outcome outcome : Outcome.values()) {
            counts.put(outcome, 0);
        }
        return counts;
    }

    private static char letter(Outcome outcome) {
        return switch (outcome) {
            case APPROVE -> 'A';
            case STEP_UP -> 'S';
            case DECLINE -> 'D';
        };
    }
}
