package com.runtimeerror.sentinel.engine;

/**
 * One comparison inside a rule's condition, checked against the facts a decision saw.
 *
 *   feature    card.txnCount.1m
 *   op         GT
 *   threshold  5
 *   actual     7, or null when the fact was missing (so the rule could not run)
 *   holds      whether this comparison was true
 *
 * The trace records what a rule did (FIRED, NOT_FIRED, ABSTAINED). Evidence shows why, one
 * comparison at a time, so the console can say "card attempts this minute: 7 > 5".
 * Inside not(...), a comparison that holds counts against the rule.
 */
public record Evidence(String feature, Operator op, double threshold, Double actual, boolean holds) {
}
