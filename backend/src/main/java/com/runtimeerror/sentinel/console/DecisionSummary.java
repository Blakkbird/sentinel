package com.runtimeerror.sentinel.console;

import com.runtimeerror.sentinel.decision.StoredDecision;
import com.runtimeerror.sentinel.engine.Outcome;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * One decision as a row in a list: the search results and the live feed. Leaves out the
 * heavy parts (features, trace); the detail page fetches those for one decision at a time.
 */
public record DecisionSummary(
        UUID decisionId,
        String transactionId,
        String cardId,
        String deviceId,
        String merchantId,
        long amountMinor,
        String currency,
        Instant decidedAt,
        Outcome outcome,
        Outcome shadowOutcome,
        List<String> reasonCodes,
        int ruleSetVersion,
        boolean degraded,
        int computeMicros
) {
    public static DecisionSummary from(StoredDecision d) {
        return new DecisionSummary(d.decisionId(), d.transactionId(), d.cardId(), d.deviceId(), d.merchantId(),
                d.amountMinor(), d.currency(), d.decidedAt(), d.outcome(), d.shadowOutcome(), d.reasonCodes(),
                d.ruleSetVersion(), d.degraded(), d.computeMicros());
    }
}
