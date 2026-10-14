package com.runtimeerror.sentinel.api;

import com.runtimeerror.sentinel.decision.StoredDecision;
import com.runtimeerror.sentinel.engine.Outcome;

import java.util.List;
import java.util.UUID;

/**
 * What a caller gets back from POST /v1/decisions.
 *
 * Deliberately small. The shadow outcome and the feature snapshot are internal: a
 * payment system calling us should never see, or come to depend on, a rule that is
 * only being tested.
 *
 * Reason codes are part of the contract, not debug output. A support agent reads them
 * to a customer, so they are stable strings and never free text.
 */
public record DecisionResponse(
        UUID decisionId,
        String transactionId,
        Outcome outcome,
        List<String> reasonCodes,
        int ruleSetVersion,
        boolean degraded
) {
    public static DecisionResponse from(StoredDecision d) {
        return new DecisionResponse(d.decisionId(), d.transactionId(), d.outcome(),
                d.reasonCodes(), d.ruleSetVersion(), d.degraded());
    }
}
