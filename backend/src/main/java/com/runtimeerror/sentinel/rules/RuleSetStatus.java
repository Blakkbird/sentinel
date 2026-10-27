package com.runtimeerror.sentinel.rules;

/**
 * Where a rule set version is in its life. Every version moves forward through these and
 * never back, except that a pending version can be withdrawn to a draft.
 *
 *            submit             approve (an admin, not the author)
 *   DRAFT  ---------->  PENDING  ------------------------------------>  PUBLISHED
 *     |    <----------     |
 *     |     withdraw       |  reject (an admin, not the author)
 *     | discard            +------------------------------------------>  REJECTED
 *     v
 *   DISCARDED
 *
 * Only a DRAFT can be edited. Once submitted, the rules are frozen, so the admin approves
 * exactly what they reviewed. A PUBLISHED version never changes again; whether it is live
 * right now is a separate question, answered by rule_set_activation (see RuleSetActivation).
 */
public enum RuleSetStatus {
    DRAFT,
    PENDING,
    PUBLISHED,
    REJECTED,
    DISCARDED
}
