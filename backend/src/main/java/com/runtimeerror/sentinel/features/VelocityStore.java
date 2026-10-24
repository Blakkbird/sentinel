package com.runtimeerror.sentinel.features;

import java.time.Instant;

/**
 * Rolling activity counters per entity (a card, a device, later a merchant), plus two
 * kinds of memory: "how many different X has this entity used lately?" and "has this
 * entity been seen with this X before?".
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

    /** Distinct values are counted in ten-minute buckets; six of them make the last hour. */
    int DISTINCT_BUCKET_MINUTES = 10;
    int DISTINCT_BUCKETS = 6;

    /**
     * Adds value to this entity's distinct set for the current ten-minute bucket, then
     * returns roughly how many different values the entity has used in the last hour,
     * including this one.
     *
     *   addDistinct("device", "dev-7", "card", "card-42", now)
     *   -> how many different cards has dev-7 tried lately?
     */
    long addDistinct(String entityType, String entityId, String dimension, String value, Instant at);

    /**
     * Remembers that value was seen with this entity, for 30 days after the latest sighting,
     * and says whether it was new and how many different values are now known.
     *
     *   addMember("card", "card-42", "device", "dev-7")
     *   -> is dev-7 a new device for card-42, and how many devices has the card used?
     */
    Membership addMember(String entityType, String entityId, String dimension, String value);

    record Membership(boolean isNew, long size) {
    }

    /** Which one-minute bucket an instant falls into: minutes since 1 January 1970. */
    static long minuteBucket(Instant at) {
        return Math.floorDiv(at.getEpochSecond(), 60);
    }

    /** Which ten-minute bucket an instant falls into. */
    static long tenMinuteBucket(Instant at) {
        return Math.floorDiv(at.getEpochSecond(), DISTINCT_BUCKET_MINUTES * 60L);
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
