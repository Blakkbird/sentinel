package com.runtimeerror.sentinel.features;

import java.time.Instant;

/**
 * Rolling activity counters per entity (a card, a device, later a merchant).
 *
 * This is an interface on purpose. The real implementation talks to Redis. Tests use an
 * in-memory version instead, so feature and rule tests need nothing running and finish
 * in milliseconds. Being able to swap the slow, failure-prone part for a stand-in under
 * test is the practical reason dependency injection exists.
 *
 * Both methods throw VelocityUnavailableException when the store cannot be reached.
 * They never return a fake zero, because "zero attempts" and "unknown" must stay distinct.
 */
public interface VelocityStore {

    /** The longest window, in minutes, that one-minute buckets can answer. */
    int MAX_WINDOW_MINUTES = 60;

    /** Records one attempt against an entity. */
    void record(String entityType, String entityId, long amountMinor, Instant at);

    /**
     * Totals over the most recent windows, e.g. read("card", id, now, 1, 60) returns the
     * last minute and the last hour, in that order, from a single round trip.
     */
    Window[] read(String entityType, String entityId, Instant at, int... windowMinutes);

    record Window(long count, long amountMinor) {
    }

    /** Which one-minute bucket an instant falls into: minutes since 1 January 1970. */
    static long minuteBucket(Instant at) {
        return Math.floorDiv(at.getEpochSecond(), 60);
    }

    /** Checks the requested windows and returns the longest one. */
    static int longestWindow(int... windowMinutes) {
        if (windowMinutes.length == 0) {
            throw new IllegalArgumentException("ask for at least one window");
        }
        int longest = 0;
        for (int minutes : windowMinutes) {
            if (minutes < 1 || minutes > MAX_WINDOW_MINUTES) {
                throw new IllegalArgumentException("window must be 1 to " + MAX_WINDOW_MINUTES + " minutes, got " + minutes);
            }
            longest = Math.max(longest, minutes);
        }
        return longest;
    }
}
