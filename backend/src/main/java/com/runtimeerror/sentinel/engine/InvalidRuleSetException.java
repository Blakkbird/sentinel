package com.runtimeerror.sentinel.engine;

import java.util.List;

/**
 * Thrown when a rule set fails validation. Carries every problem found, not just the
 * first, so whoever is editing rules can fix them all in one go.
 */
public class InvalidRuleSetException extends RuntimeException {

    private final List<String> problems;

    public InvalidRuleSetException(List<String> problems) {
        super("rule set is invalid:\n  - " + String.join("\n  - ", problems));
        this.problems = List.copyOf(problems);
    }

    public List<String> problems() {
        return problems;
    }
}
