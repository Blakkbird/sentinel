package com.runtimeerror.sentinel.engine;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * The "if" part of a rule, stored as a small tree.
 *
 *   card.txnCount.1m > 5                         a Compare leaf
 *
 *   all(sys.velocityAvailable == 0,              an All node holding two leaves
 *       txn.amountMinor > 1000000)
 *
 * Why a tree of objects instead of a string like "count > 5 && amount > 100"?
 * A string needs a parser, and writing a parser is a two week detour that adds nothing
 * to the product. A tree maps directly onto JSON for storage (stage 3) and onto a visual
 * rule builder in the console (stage 5), and evaluating it is just method calls.
 *
 * "sealed" means only the four record types declared inside this file may implement
 * Condition. The compiler then knows every possible shape a condition can take, which
 * keeps any code that walks the tree (validation, JSON conversion) complete.
 */
public sealed interface Condition {

    /** Evaluates against a feature vector that is known to contain every required feature. */
    boolean test(FeatureVector features);

    /** Adds the name of every feature this condition reads into the given set. */
    void collectFeatures(Set<String> into);

    /** Human readable form, shown in the console and in error messages. */
    String describe();

    /** Every feature this condition reads, sorted. */
    default Set<String> features() {
        Set<String> names = new TreeSet<>();
        collectFeatures(names);
        return Collections.unmodifiableSet(names);
    }

    // ---- leaf: compare one feature against a constant --------------------------------

    record Compare(String feature, Operator op, double value) implements Condition {
        public Compare {
            Objects.requireNonNull(feature, "feature");
            Objects.requireNonNull(op, "op");
            if (!Double.isFinite(value)) {
                throw new IllegalArgumentException("comparison value must be a finite number");
            }
        }

        @Override
        public boolean test(FeatureVector features) {
            return op.apply(features.get(feature), value);
        }

        @Override
        public void collectFeatures(Set<String> into) {
            into.add(feature);
        }

        @Override
        public String describe() {
            return feature + " " + op.symbol() + " " + formatNumber(value);
        }
    }

    // ---- branches ---------------------------------------------------------------------

    /** True when every part is true. */
    record All(List<Condition> parts) implements Condition {
        public All {
            parts = List.copyOf(parts);   // defensive copy; also rejects null entries
            if (parts.isEmpty()) {
                throw new IllegalArgumentException("all() needs at least one condition");
            }
        }

        @Override
        public boolean test(FeatureVector features) {
            // A plain loop rather than a stream. This runs on every decision, and a stream
            // allocates helper objects that a loop does not.
            for (Condition part : parts) {
                if (!part.test(features)) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public void collectFeatures(Set<String> into) {
            for (Condition part : parts) {
                part.collectFeatures(into);
            }
        }

        @Override
        public String describe() {
            return "all(" + joinDescriptions(parts) + ")";
        }
    }

    /** True when at least one part is true. */
    record Any(List<Condition> parts) implements Condition {
        public Any {
            parts = List.copyOf(parts);
            if (parts.isEmpty()) {
                throw new IllegalArgumentException("any() needs at least one condition");
            }
        }

        @Override
        public boolean test(FeatureVector features) {
            for (Condition part : parts) {
                if (part.test(features)) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public void collectFeatures(Set<String> into) {
            for (Condition part : parts) {
                part.collectFeatures(into);
            }
        }

        @Override
        public String describe() {
            return "any(" + joinDescriptions(parts) + ")";
        }
    }

    /** True when the inner condition is false. */
    record Not(Condition inner) implements Condition {
        public Not {
            Objects.requireNonNull(inner, "inner");
        }

        @Override
        public boolean test(FeatureVector features) {
            return !inner.test(features);
        }

        @Override
        public void collectFeatures(Set<String> into) {
            inner.collectFeatures(into);
        }

        @Override
        public String describe() {
            return "not(" + inner.describe() + ")";
        }
    }

    // ---- factory methods, so rules read naturally: gt("card.txnCount.1m", 5) ---------

    static Condition gt(String feature, double value) {
        return new Compare(feature, Operator.GT, value);
    }

    static Condition gte(String feature, double value) {
        return new Compare(feature, Operator.GTE, value);
    }

    static Condition lt(String feature, double value) {
        return new Compare(feature, Operator.LT, value);
    }

    static Condition lte(String feature, double value) {
        return new Compare(feature, Operator.LTE, value);
    }

    static Condition eq(String feature, double value) {
        return new Compare(feature, Operator.EQ, value);
    }

    static Condition neq(String feature, double value) {
        return new Compare(feature, Operator.NEQ, value);
    }

    static Condition all(Condition... parts) {
        return new All(List.of(parts));
    }

    static Condition any(Condition... parts) {
        return new Any(List.of(parts));
    }

    static Condition not(Condition inner) {
        return new Not(inner);
    }

    // ---- helpers ------------------------------------------------------------------------

    private static String formatNumber(double value) {
        // print 5 rather than 5.0 when the number is whole
        if (value == Math.rint(value) && Math.abs(value) < 1e15) {
            return Long.toString((long) value);
        }
        return Double.toString(value);
    }

    private static String joinDescriptions(List<Condition> parts) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) {
                out.append(", ");
            }
            out.append(parts.get(i).describe());
        }
        return out.toString();
    }
}
