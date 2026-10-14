package com.runtimeerror.sentinel.engine;

/** How a rule participates in decisions. */
public enum RuleMode {

    /** Kept in the rule set for history, but never evaluated. */
    OFF,

    /**
     * Evaluated and recorded in the decision trace, but cannot change the outcome.
     * This is how a new rule is tried on real traffic without any risk: it watches,
     * and we count how many decisions it would have changed.
     */
    SHADOW,

    /** Evaluated, and its proposal counts toward the outcome. */
    ACTIVE
}
