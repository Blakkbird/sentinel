package com.runtimeerror.sentinel.engine;

/**
 * One line of the decision trace: what a single rule did for a single transaction.
 * The full trace is stored with every decision, so the console can show exactly which
 * rules fired, which did not, and which could not run.
 */
public record RuleEvaluation(
        String ruleCode,
        RuleMode mode,
        Result result,
        Outcome proposes,
        String reasonCode
) {
    public enum Result {
        /** The condition was true. */
        FIRED,
        /** The condition was false. */
        NOT_FIRED,
        /**
         * A feature the rule needs was missing, so the rule gave no opinion.
         * This is deliberately different from NOT_FIRED. "The count is zero" and
         * "we could not read the count" must never be confused.
         */
        ABSTAINED
    }
}
