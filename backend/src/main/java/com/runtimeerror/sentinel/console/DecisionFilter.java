package com.runtimeerror.sentinel.console;

import com.runtimeerror.sentinel.engine.Outcome;

/**
 * What an analyst is searching for. Every part is optional.
 *
 *   outcome            only APPROVE, STEP_UP or DECLINE
 *   query              an exact transaction, card, device or merchant id
 *   degradedOnly       only decisions made while Redis was down
 *   shadowChangesOnly  only decisions a shadow rule would have changed
 */
public record DecisionFilter(Outcome outcome, String query, boolean degradedOnly, boolean shadowChangesOnly) {

    public DecisionFilter {
        query = query == null || query.isBlank() ? null : query.strip();
    }
}
