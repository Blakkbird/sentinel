package com.runtimeerror.sentinel.rules;

/** No rule set has this version number. Becomes a 404. */
public class RuleSetNotFoundException extends RuntimeException {

    public RuleSetNotFoundException(int version) {
        super("There is no rule set version " + version + ".");
    }
}
