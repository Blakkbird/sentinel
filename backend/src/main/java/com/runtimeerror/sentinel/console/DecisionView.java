package com.runtimeerror.sentinel.console;

import com.runtimeerror.sentinel.engine.Evidence;
import com.runtimeerror.sentinel.engine.Outcome;
import com.runtimeerror.sentinel.engine.RuleEvaluation;
import com.runtimeerror.sentinel.engine.RuleMode;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Everything the console's decision page shows: the stored decision, its facts with their
 * descriptions, and every rule with its result, its text and its evidence.
 */
public record DecisionView(
        UUID decisionId,
        String transactionId,
        String cardId,
        String deviceId,
        String merchantId,
        long amountMinor,
        String currency,
        Instant occurredAt,
        Instant decidedAt,
        Outcome outcome,
        Outcome shadowOutcome,
        List<String> reasonCodes,
        int ruleSetVersion,
        boolean degraded,
        int computeMicros,
        List<Fact> facts,
        List<RuleResult> rules
) {
    /** One fact the rules saw. description is null if the feature is no longer catalogued. */
    public record Fact(String name, double value, String description) {
    }

    /**
     * One rule's part in the decision. code, mode, result, proposes and reasonCode come from
     * the stored trace and are exactly what happened. description, condition and evidence
     * come from the rule's definition, taken from definitionVersion (normally the version
     * that made the decision).
     */
    public record RuleResult(
            String code,
            RuleMode mode,
            RuleEvaluation.Result result,
            Outcome proposes,
            String reasonCode,
            String description,
            String condition,
            Integer definitionVersion,
            List<Evidence> evidence
    ) {
    }
}
