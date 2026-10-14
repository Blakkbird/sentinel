package com.runtimeerror.sentinel.engine;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Every fact a rule is allowed to look at, frozen at the moment of the decision.
 *
 * A "feature" is one named number, for example card.txnCount.1m = 6, meaning this card
 * was used 6 times in the last minute. The feature vector is the full set of them for
 * one transaction.
 *
 * Three properties matter, and each one is deliberate:
 *
 *  1. Immutable. Once built it never changes. If a rule could modify it, the order in
 *     which rules run would change the result, and replaying old decisions would break.
 *
 *  2. Self contained. A rule sees only this object. No database, no network, no clock.
 *     That is what lets us store it and re-run any rule set against it later.
 *
 *  3. Sorted by name (TreeMap). Stored copies always come out in the same order, which
 *     makes two snapshots easy to compare.
 *
 * Values are plain doubles so that rules stored as data (JSON in the database, from
 * stage 3) can refer to features by name without any Java code changing. The price is
 * type safety, which the FeatureCatalogue buys back by checking every name a rule uses
 * at the moment the rule set is compiled.
 */
public final class FeatureVector {

    private final Map<String, Double> values;

    private FeatureVector(Map<String, Double> values) {
        this.values = values;
    }

    /** Rebuilds a vector from stored values, for example when replaying an old decision. */
    public static FeatureVector of(Map<String, ? extends Number> raw) {
        Builder builder = new Builder();
        raw.forEach((name, value) -> builder.put(name, value.doubleValue()));
        return builder.build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public boolean has(String name) {
        return values.containsKey(name);
    }

    public boolean hasAll(Collection<String> names) {
        return values.keySet().containsAll(names);
    }

    /** The value of a feature. Throws if it is absent, so check has() first. */
    public double get(String name) {
        Double value = values.get(name);
        if (value == null) {
            throw new IllegalArgumentException("feature not present: " + name);
        }
        return value;
    }

    /** Read-only view, used when storing the snapshot. */
    public Map<String, Double> asMap() {
        return values;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof FeatureVector other && values.equals(other.values);
    }

    @Override
    public int hashCode() {
        return values.hashCode();
    }

    @Override
    public String toString() {
        return values.toString();
    }

    public static final class Builder {
        private final Map<String, Double> values = new TreeMap<>();

        public Builder put(String name, double value) {
            Objects.requireNonNull(name, "feature name");
            if (!Double.isFinite(value)) {
                // NaN and Infinity cannot be stored as JSON, and any comparison against
                // NaN quietly returns false, so a rule would silently never fire.
                throw new IllegalArgumentException("feature " + name + " is not a finite number: " + value);
            }
            values.put(name, value);
            return this;
        }

        public FeatureVector build() {
            return new FeatureVector(Collections.unmodifiableMap(new TreeMap<>(values)));
        }
    }
}
