package com.runtimeerror.sentinel.engine;

/**
 * The three possible answers to "should this payment go through?"
 *
 *   APPROVE  let it through
 *   STEP_UP  ask the customer to prove it is them (an OTP, a 3-D Secure check)
 *   DECLINE  block it
 *
 * When several rules fire and disagree, the most severe answer wins. That policy lives
 * here, in one place, instead of being scattered through the code.
 */
public enum Outcome {
    APPROVE(0),
    STEP_UP(1),
    DECLINE(2);

    private final int severity;

    Outcome(int severity) {
        this.severity = severity;
    }

    public int severity() {
        return severity;
    }

    /** Returns whichever of the two outcomes is more severe. */
    public Outcome mostSevere(Outcome other) {
        return other.severity > this.severity ? other : this;
    }
}
