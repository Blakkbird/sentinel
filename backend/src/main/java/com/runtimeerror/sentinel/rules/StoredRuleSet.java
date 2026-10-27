package com.runtimeerror.sentinel.rules;

import com.runtimeerror.sentinel.engine.RuleDefinition;

import java.time.Instant;
import java.util.List;

/**
 * One row of the rule_set table: a version of the rules plus who did what to it, and when.
 *
 *   basedOn    the published version this one was written as a change to (null for version 1)
 *   revision   goes up by one on every save of a draft; see RuleSetStore.updateDraft
 *   createdBy  the author's username: the "maker" in maker-checker
 *   reviewedBy the admin who approved or rejected it: the "checker"
 *
 * Every rule set in the table compiles. Nothing is stored before RuleSet.compile accepts it,
 * so code that reads a StoredRuleSet never has to wonder whether its rules are valid.
 */
public record StoredRuleSet(
        int version,
        RuleSetStatus status,
        Integer basedOn,
        String title,
        List<RuleDefinition> rules,
        int revision,
        String createdBy,
        String createdByName,
        Instant createdAt,
        Instant updatedAt,
        Instant submittedAt,
        String reviewedBy,
        String reviewedByName,
        Instant reviewedAt,
        String reviewNote
) {
    public StoredRuleSet {
        rules = List.copyOf(rules);
    }
}
