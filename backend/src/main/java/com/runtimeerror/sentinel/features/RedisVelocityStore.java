package com.runtimeerror.sentinel.features;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Velocity counters kept in Redis as one-minute buckets.
 *
 * How it works
 *  - Time is cut into one-minute buckets, numbered by minutes since 1970.
 *  - Each attempt adds to two keys for the current bucket: a count and an amount.
 *      v:card:card-42:count:29512345  ->  3
 *      v:card:card-42:amount:29512345 ->  14700
 *  - A window of N minutes is the sum of the newest N buckets. All the keys are fetched
 *    with one MGET, so any read is a single network round trip.
 *  - Keys expire on their own after 70 minutes, so Redis never fills up with old buckets.
 *
 * The approximation, stated honestly
 *  A window of N buckets covers between N-1 and N minutes of real time, because the
 *  newest bucket is only partly filled. For the one-hour window that hardly matters.
 *  For the one-minute window it matters a lot: "the last minute" is really "this clock
 *  minute", so at 10:00:02 it covers two seconds, and a burst that straddles the
 *  boundary is split across two buckets and can slip under a threshold. That is a known
 *  weakness of fixed windows and a stage 2 task (smaller buckets, or weighting the
 *  previous bucket). An exact sliding window needs a Redis sorted set per entity, which
 *  costs memory per event rather than per minute.
 */
@Component
public class RedisVelocityStore implements VelocityStore {

    private static final Duration BUCKET_TTL = Duration.ofMinutes(MAX_WINDOW_MINUTES + 10);

    private final StringRedisTemplate redis;

    public RedisVelocityStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public void record(String entityType, String entityId, long amountMinor, Instant at) {
        long bucket = VelocityStore.minuteBucket(at);
        try {
            bump(key(entityType, entityId, "count", bucket), 1);
            bump(key(entityType, entityId, "amount", bucket), amountMinor);
        } catch (RuntimeException e) {
            throw new VelocityUnavailableException("could not record " + entityType + " attempt", e);
        }
    }

    private void bump(String key, long delta) {
        Long value = redis.opsForValue().increment(key, delta);
        // INCRBY returns the new total. If the total equals delta, this call created the
        // key, so this is the one moment to give it an expiry. Later increments skip the
        // extra call. Stage 2 folds both into a single pipelined round trip.
        if (value != null && value == delta) {
            redis.expire(key, BUCKET_TTL);
        }
    }

    @Override
    public Window[] read(String entityType, String entityId, Instant at, int... windowMinutes) {
        int longest = VelocityStore.longestWindow(windowMinutes);
        long current = VelocityStore.minuteBucket(at);

        // one MGET for every bucket we need: all the counts first, then all the amounts
        List<String> keys = new ArrayList<>(longest * 2);
        for (int i = 0; i < longest; i++) {
            keys.add(key(entityType, entityId, "count", current - i));
        }
        for (int i = 0; i < longest; i++) {
            keys.add(key(entityType, entityId, "amount", current - i));
        }

        List<String> values;
        try {
            values = redis.opsForValue().multiGet(keys);
        } catch (RuntimeException e) {
            throw new VelocityUnavailableException("could not read " + entityType + " counters", e);
        }
        if (values == null || values.size() != keys.size()) {
            throw new VelocityUnavailableException("unexpected reply from Redis for " + entityType, null);
        }

        // Prefix sums: countUpTo[k] is the total over the newest k buckets. Any window
        // length is then answered from the same single read.
        long[] countUpTo = new long[longest + 1];
        long[] amountUpTo = new long[longest + 1];
        for (int i = 0; i < longest; i++) {
            countUpTo[i + 1] = countUpTo[i] + parse(values.get(i));
            amountUpTo[i + 1] = amountUpTo[i] + parse(values.get(longest + i));
        }

        Window[] windows = new Window[windowMinutes.length];
        for (int w = 0; w < windowMinutes.length; w++) {
            windows[w] = new Window(countUpTo[windowMinutes[w]], amountUpTo[windowMinutes[w]]);
        }
        return windows;
    }

    private static long parse(String value) {
        return value == null ? 0 : Long.parseLong(value);   // a missing key is an empty bucket
    }

    private static String key(String entityType, String entityId, String measure, long bucket) {
        return "v:" + entityType + ":" + entityId + ":" + measure + ":" + bucket;
    }
}
