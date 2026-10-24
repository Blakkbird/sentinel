package com.runtimeerror.sentinel.decision;

/**
 * Announced after a new decision has been written to the log. The live feed listens for it.
 *
 * Only new decisions are announced. A retry that gets a stored decision back is not news.
 * Using an event means DecisionService does not know (or care) who is listening: the live
 * feed today, maybe alerting or a dashboard counter later.
 */
public record DecisionRecorded(StoredDecision decision) {
}
