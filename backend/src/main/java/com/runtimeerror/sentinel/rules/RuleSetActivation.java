package com.runtimeerror.sentinel.rules;

import java.time.Instant;

/**
 * One row of rule_set_activation: "from this moment, this version was live".
 *
 *   SEED      put in place by a migration (versions 1 and 2, by V3)
 *   PUBLISH   an admin approved a new version and it went live
 *   ROLLBACK  an admin made an older published version live again
 *
 * The newest row is the live version. Rows are only ever added, so the table is the history
 * of every change of live rules, and a rollback is simply one more row.
 */
public record RuleSetActivation(long id, int version, Kind kind, String activatedBy, String activatedByName,
                                Instant activatedAt) {

    public enum Kind {
        SEED,
        PUBLISH,
        ROLLBACK
    }
}
