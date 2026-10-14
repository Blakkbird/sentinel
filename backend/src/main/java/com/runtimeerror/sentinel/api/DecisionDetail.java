package com.runtimeerror.sentinel.api;

import com.runtimeerror.sentinel.decision.StoredDecision;
import com.runtimeerror.sentinel.engine.Outcome;
import com.runtimeerror.sentinel.engine.RuleEvaluation;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Everything about one past decision, for GET /v1/decisions/{transactionId}.
 * This is the "why did this happen" view an analyst or auditor uses.
 */
public record DecisionDetail(
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
        Map<String, Double> features,
        List<RuleEvaluation> trace,
        int computeMicros
) {
    public static DecisionDetail from(StoredDecision d) {
        return new DecisionDetail(d.decisionId(), d.transactionId(), d.cardId(), d.deviceId(),
                d.merchantId(), d.amountMinor(), d.currency(), d.occurredAt(), d.decidedAt(),
                d.outcome(), d.shadowOutcome(), d.reasonCodes(), d.ruleSetVersion(), d.degraded(),
                d.features().asMap(), d.trace(), d.computeMicros());
    }
}
