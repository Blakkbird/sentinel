package com.runtimeerror.sentinel.rules;

/**
 * A rule workflow step was refused, with the reason in words a person can act on.
 *
 *   forbidden  this person may not do this at all (not the author, not an admin): 403
 *   otherwise  nobody can do this right now (it is not a draft any more, or it is out of date): 409
 */
public class RuleSetRefusedException extends RuntimeException {

    private final boolean forbidden;

    public RuleSetRefusedException(boolean forbidden, String reason) {
        super(reason);
        this.forbidden = forbidden;
    }

    public boolean forbidden() {
        return forbidden;
    }
}
