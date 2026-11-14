package com.runtimeerror.sentinel.console;

import com.runtimeerror.sentinel.engine.RuleMode;

import java.time.Instant;
import java.util.List;

/**
 * Everything the dashboard page shows, for one window of time.
 *
 * Only counts are sent. Shares (92% approved, 3% degraded) are worked out in the console,
 * which has to handle "no decisions at all" anyway, so nothing here ever divides.
 *
 *   window         15m, 1h or 24h, as asked for
 *   generatedAt    the server's clock when the numbers were read
 *   bucketSeconds  how long one point of the series covers
 *   series         one point per bucket, oldest first; a bucket with no decisions is all zeros
 *   topRules       the rules that fired most, most first; at most eight
 */
public record DashboardView(
        String window,
        Instant generatedAt,
        long bucketSeconds,
        Totals totals,
        ComputeMicros computeMicros,
        List<Point> series,
        List<TopRule> topRules
) {

    /** shadowChanges counts decisions where a shadow rule would have given a different outcome. */
    public record Totals(long decisions, long approve, long stepUp, long decline, long degraded, long shadowChanges) {
    }

    /**
     * How long deciding took, in microseconds. p50 is the middle decision; p95 is the one that
     * 95 in 100 were faster than. Both are null when the window holds no decisions: there is no
     * honest number to give, and zero would read as "instant".
     */
    public record ComputeMicros(Double p50, Double p95) {
    }

    /** The decisions made from "at" up to one bucket later, split by outcome. */
    public record Point(Instant at, long approve, long stepUp, long decline) {
    }

    /**
     * How often one rule fired. A rule in SHADOW mode is listed too, marked by its mode: it
     * fired, but changed nothing. A rule whose mode changed inside the window appears once per mode.
     */
    public record TopRule(String ruleCode, RuleMode mode, long count) {
    }
}
