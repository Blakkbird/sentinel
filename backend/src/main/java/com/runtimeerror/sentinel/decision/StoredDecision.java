package com.runtimeerror.sentinel.decision;

import com.runtimeerror.sentinel.engine.FeatureVector;
import com.runtimeerror.sentinel.engine.Outcome;
import com.runtimeerror.sentinel.engine.RuleEvaluation;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * One row of the decision log: everything needed to explain or replay a decision.
 *
 * features and trace are the important parts. They freeze what the rules saw and what
 * each rule did, at that moment. Counters in Redis keep moving, so recomputing them
 * later would give different numbers. The stored snapshot is the only honest record.
 *
 * occurredAt is when the caller says the payment happened. decidedAt is our own clock.
 * Velocity uses decidedAt, because a timestamp sent by a caller can be wrong or late.
 */
public record StoredDecision(
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
        FeatureVector features,
        List<RuleEvaluation> trace,
        int computeMicros
) {
}
