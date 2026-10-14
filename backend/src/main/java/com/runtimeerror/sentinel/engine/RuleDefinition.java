package com.runtimeerror.sentinel.engine;

import java.util.Objects;

/**
 * A rule as written by a person: pure data, not yet checked.
 *
 *   code         stable identifier, e.g. VEL_CARD_1M. Never reuse a code for a different rule.
 *   description  plain English, shown to analysts
 *   condition    when the rule fires
 *   proposes     what it asks for when it fires: STEP_UP or DECLINE
 *   reasonCode   short stable string returned to the caller, e.g. CARD_VELOCITY_1M
 *   mode         OFF, SHADOW or ACTIVE
 *
 * Definitions are turned into a runnable RuleSet by RuleSet.compile(), which checks them.
 * Keeping "what someone wrote" separate from "what is ready to run" is the same idea as
 * source code versus a compiled program.
 */
public record RuleDefinition(
        String code,
        String description,
        Condition condition,
        Outcome proposes,
        String reasonCode,
        RuleMode mode
) {
    public RuleDefinition {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(condition, "condition");
        Objects.requireNonNull(proposes, "proposes");
        Objects.requireNonNull(reasonCode, "reasonCode");
        Objects.requireNonNull(mode, "mode");
    }
}
