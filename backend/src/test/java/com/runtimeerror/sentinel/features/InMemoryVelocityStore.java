package com.runtimeerror.sentinel.features;

import java.time.Instant;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A stand-in for RedisVelocityStore, used in tests. Same one-minute buckets, same
 * windows, same behaviour when "down", but everything lives in a map in memory.
 *
 * Keep it honest. In stage 1 you will write one contract test that runs identical
 * checks against this and against real Redis, so the two can never drift apart.
 * A fake that behaves differently from the real thing is worse than no fake at all.
 */
public class InMemoryVelocityStore implements VelocityStore {

    private final Map<String, long[]> buckets = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> distinct = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> members = new ConcurrentHashMap<>();
    private volatile boolean available = true;

    /** Simulates an outage. */
    public void setAvailable(boolean available) {
        this.available = available;
    }

    @Override
    public void record(String entityType, String entityId, long amountMinor, Instant at) {
        requireAvailable();
        String key = key(entityType, entityId, VelocityStore.minuteBucket(at));
        buckets.merge(key, new long[]{1, amountMinor},
                (old, add) -> new long[]{old[0] + add[0], old[1] + add[1]});
    }

    @Override
    public Window[] read(String entityType, String entityId, Instant at, int... windowMinutes) {
        requireAvailable();
        int longest = VelocityStore.longestWindow(windowMinutes);
        long current = VelocityStore.minuteBucket(at);

        long[] countUpTo = new long[longest + 1];
        long[] amountUpTo = new long[longest + 1];
        for (int i = 0; i < longest; i++) {
            long[] bucket = buckets.getOrDefault(key(entityType, entityId, current - i), new long[]{0, 0});
            countUpTo[i + 1] = countUpTo[i] + bucket[0];
            amountUpTo[i + 1] = amountUpTo[i] + bucket[1];
        }

        Window[] windows = new Window[windowMinutes.length];
        for (int w = 0; w < windowMinutes.length; w++) {
            windows[w] = new Window(countUpTo[windowMinutes[w]], amountUpTo[windowMinutes[w]]);
        }
        return windows;
    }

    @Override
    public long addDistinct(String entityType, String entityId, String dimension, String value, Instant at) {
        requireAvailable();
        String entity = entityType + ":" + dimension;
        long bucket = VelocityStore.tenMinuteBucket(at);
        distinct.computeIfAbsent(key(entity, entityId, bucket), k -> ConcurrentHashMap.newKeySet()).add(value);

        Set<String> lastHour = new HashSet<>();
        for (int i = 0; i < DISTINCT_BUCKETS; i++) {
            lastHour.addAll(distinct.getOrDefault(key(entity, entityId, bucket - i), Set.of()));
        }
        return lastHour.size();   // exact, where Redis is approximate; at test sizes they agree
    }

    @Override
    public Membership addMember(String entityType, String entityId, String dimension, String value) {
        requireAvailable();
        Set<String> seen = members.computeIfAbsent(entityType + ":" + entityId + ":" + dimension,
                k -> ConcurrentHashMap.newKeySet());
        boolean isNew = seen.add(value);
        return new Membership(isNew, seen.size());
    }

    private void requireAvailable() {
        if (!available) {
            throw new VelocityUnavailableException("in-memory store switched off for this test", null);
        }
    }

    private static String key(String entityType, String entityId, long bucket) {
        return entityType + ":" + entityId + ":" + bucket;
    }
}
