package com.runtimeerror.sentinel.engine;

import java.util.List;

/**
 * What the engine concluded for one transaction.
 *
 *   outcome         the live answer, from ACTIVE rules only
 *   reasonCodes     why, one code per ACTIVE rule that fired, in rule order
 *   shadowOutcome   what the answer would have been if every SHADOW rule were ACTIVE
 *   trace           what every non-OFF rule did, including shadow rules
 *   ruleSetVersion  which rule set produced this
 *
 * Note that shadowOutcome can only ever be the same as outcome or stricter. Since the
 * most severe proposal wins, adding a rule can never loosen a decision. So shadow mode
 * tests new rules; testing a loosened threshold is the job of replay (stage 4).
 */
public record Verdict(
        Outcome outcome,
        List<String> reasonCodes,
        Outcome shadowOutcome,
        List<RuleEvaluation> trace,
        int ruleSetVersion
) {
    public Verdict {
        reasonCodes = List.copyOf(reasonCodes);
        trace = List.copyOf(trace);
    }

    /** True when promoting the shadow rules would have changed this decision. */
    public boolean shadowWouldChange() {
        return shadowOutcome != outcome;
    }
}
