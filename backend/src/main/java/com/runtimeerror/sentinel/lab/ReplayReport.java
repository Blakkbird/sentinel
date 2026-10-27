package com.runtimeerror.sentinel.lab;

import com.runtimeerror.sentinel.engine.Outcome;
import com.runtimeerror.sentinel.engine.RuleMode;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * What the Rule Lab found: how a rule set version would have decided recent payments, next to
 * how the live rules decide them, using the exact facts each decision recorded.
 *
 *   version / liveVersion           the version tried, and the live version it is compared with
 *   decisions, from, to             how many past decisions were replayed, and their time span
 *   liveOutcomes / versionOutcomes  how many of each outcome under each rule set
 *   changed, moves                  how many outcomes differ, by kind (APPROVE -> DECLINE: 35)
 *   rules                           per rule: how often it fires under each, and how often it could not run
 *   examples                        the most recent decisions that would change (up to 50)
 *   liveStrip / versionStrip        one letter per decision, oldest first (A, S, D), for the console's strip
 *   check                           re-running each decision with its own original rules: did we get
 *                                   the recorded outcome back? It should always say yes.
 */
public record ReplayReport(
        int version,
        int liveVersion,
        int decisions,
        Instant from,
        Instant to,
        Map<Outcome, Integer> liveOutcomes,
        Map<Outcome, Integer> versionOutcomes,
        int changed,
        List<Move> moves,
        List<RuleImpact> rules,
        List<ChangedDecision> examples,
        String liveStrip,
        String versionStrip,
        Check check
) {

    /** "count decisions would go from this outcome to that one". */
    public record Move(Outcome from, Outcome to, int count) {
    }

    /**
     * One rule's activity. liveFired is null for a rule the live set does not have (new), and
     * versionFired is null for a rule this version removes. abstained counts decisions where the
     * rule could not run because a fact it needs was not recorded (for example, no device id).
     */
    public record RuleImpact(String code, RuleMode mode, Outcome proposes, Integer liveFired, Integer versionFired,
                             int versionAbstained) {
    }

    public record ChangedDecision(String transactionId, Instant decidedAt, String cardId, long amountMinor,
                                  String currency, Outcome before, Outcome after, List<String> reasonCodes) {
    }

    /** checked: decisions whose own version could be run again. matched: those that came out exactly as recorded. */
    public record Check(int checked, int matched, int unknownVersion) {
    }
}
