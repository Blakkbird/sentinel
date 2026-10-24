package com.runtimeerror.sentinel.engine;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns a rule's condition into evidence: every comparison in it, next to the value the
 * decision actually saw.
 *
 * Pure, like the engine: the same condition and the same facts always give the same
 * evidence. A stored decision keeps its facts, so its evidence can be rebuilt at any time
 * and will always agree with what happened.
 *
 * The switch below is Java 21 pattern matching over a sealed interface. Condition permits
 * exactly four record types, so the compiler checks that every one is handled; adding a
 * fifth kind of condition would make this fail to compile until it is explained too.
 */
public final class Explainer {

    private Explainer() {
    }

    public static List<Evidence> evidence(Condition condition, FeatureVector features) {
        List<Evidence> out = new ArrayList<>();
        collect(condition, features, out);
        return List.copyOf(out);
    }

    private static void collect(Condition condition, FeatureVector features, List<Evidence> out) {
        switch (condition) {
            case Condition.Compare compare -> {
                Double actual = features.has(compare.feature()) ? features.get(compare.feature()) : null;
                boolean holds = actual != null && compare.op().apply(actual, compare.value());
                out.add(new Evidence(compare.feature(), compare.op(), compare.value(), actual, holds));
            }
            case Condition.All all -> all.parts().forEach(part -> collect(part, features, out));
            case Condition.Any any -> any.parts().forEach(part -> collect(part, features, out));
            case Condition.Not not -> collect(not.inner(), features, out);
        }
    }
}
