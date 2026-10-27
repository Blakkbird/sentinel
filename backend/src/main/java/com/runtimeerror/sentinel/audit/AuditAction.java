package com.runtimeerror.sentinel.audit;

/** Everything the audit log records. Stored by name, so never rename one; add new ones instead. */
public enum AuditAction {
    RULES_MOVED_TO_DATABASE,
    RULE_SET_DRAFTED,
    RULE_SET_DISCARDED,
    RULE_SET_SUBMITTED,
    RULE_SET_WITHDRAWN,
    RULE_SET_PUBLISHED,
    RULE_SET_REJECTED,
    RULE_SET_ROLLED_BACK
}
