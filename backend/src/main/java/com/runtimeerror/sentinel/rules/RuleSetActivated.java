package com.runtimeerror.sentinel.rules;

/**
 * Announced after a different version has gone live, by a publish or a rollback, once the
 * database has recorded it. The live feed passes it to every open console, which says so on
 * screen. Same idea as DecisionRecorded: whoever changes the rules does not need to know who
 * is listening.
 */
public record RuleSetActivated(int version, int replaced, RuleSetActivation.Kind kind, String byName, String title) {
}
