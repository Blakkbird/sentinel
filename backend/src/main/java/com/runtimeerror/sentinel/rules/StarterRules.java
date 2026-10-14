package com.runtimeerror.sentinel.rules;

import com.runtimeerror.sentinel.engine.RuleDefinition;
import com.runtimeerror.sentinel.features.Features;

import java.util.List;

import static com.runtimeerror.sentinel.engine.Condition.all;
import static com.runtimeerror.sentinel.engine.Condition.eq;
import static com.runtimeerror.sentinel.engine.Condition.gt;
import static com.runtimeerror.sentinel.engine.Outcome.DECLINE;
import static com.runtimeerror.sentinel.engine.Outcome.STEP_UP;
import static com.runtimeerror.sentinel.engine.RuleMode.ACTIVE;
import static com.runtimeerror.sentinel.engine.RuleMode.SHADOW;

/**
 * Version 1 of the rules, written in code for now.
 *
 * From stage 3 rules live in the database as JSON, are edited from the console, and are
 * published as new versions without restarting anything. The RuleDefinition objects
 * below are exactly the shape that JSON will take, so nothing else changes then.
 *
 * Amounts are in paise, written with Indian digit grouping so they read naturally:
 *   50_000_00  = 5,000,000 paise = Rs 50,000
 *   1_00_000_00 = 10,000,000 paise = Rs 1,00,000
 * (Java allows underscores anywhere between digits; they are ignored by the compiler.)
 */
public final class StarterRules {

    public static final int VERSION = 1;

    public static List<RuleDefinition> definitions() {
        return List.of(
                new RuleDefinition(
                        "AMT_HIGH",
                        "Step up any single payment above Rs 50,000",
                        gt(Features.TXN_AMOUNT_MINOR, 50_000_00),
                        STEP_UP, "AMOUNT_ABOVE_LIMIT", ACTIVE),

                new RuleDefinition(
                        "VEL_CARD_1M",
                        "Decline a card used more than 5 times in a minute. This is card testing:"
                                + " a fraudster with stolen card numbers fires small payments to see which cards work",
                        gt(Features.CARD_COUNT_1M, 5),
                        DECLINE, "CARD_VELOCITY_1M", ACTIVE),

                new RuleDefinition(
                        "VEL_CARD_1M_STRICT",
                        "Candidate replacement for VEL_CARD_1M with a tighter limit of 3."
                                + " Running in shadow so we can see how many extra declines it would cause",
                        gt(Features.CARD_COUNT_1M, 3),
                        DECLINE, "CARD_VELOCITY_1M_STRICT", SHADOW),

                new RuleDefinition(
                        "SPEND_CARD_1H",
                        "Step up when a card attempts more than Rs 1,00,000 in total within an hour",
                        gt(Features.CARD_AMOUNT_1H, 1_00_000_00),
                        STEP_UP, "CARD_SPEND_1H", ACTIVE),

                new RuleDefinition(
                        "DEVICE_BURST_1H",
                        "Step up when one device makes more than 20 payments in an hour",
                        gt(Features.DEVICE_COUNT_1H, 20),
                        STEP_UP, "DEVICE_VELOCITY_1H", ACTIVE),

                // The fallback for when Redis is down is itself just a rule. That means it
                // is versioned, visible in the console, and replays exactly like any other,
                // instead of being a special case buried in Java code.
                new RuleDefinition(
                        "DEGRADED_FALLBACK",
                        "When velocity data is unavailable, step up anything above Rs 10,000",
                        all(eq(Features.SYS_VELOCITY_AVAILABLE, 0), gt(Features.TXN_AMOUNT_MINOR, 10_000_00)),
                        STEP_UP, "DEGRADED_FALLBACK", ACTIVE)
        );
    }

    private StarterRules() {
    }
}
