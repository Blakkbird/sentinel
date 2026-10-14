package com.runtimeerror.sentinel.engine;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * The list of every feature that exists, with a description of each.
 *
 * Its job is to catch typos before they reach production. Because rules refer to
 * features by name, a rule mentioning "card.txnCount.1min" instead of "card.txnCount.1m"
 * would otherwise compile, deploy, and then quietly never fire. With a catalogue, that
 * rule set is rejected at compile time with a message naming the rule and the bad name.
 */
public final class FeatureCatalogue {

    public record FeatureSpec(String name, String description) {
        public FeatureSpec {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(description, "description");
        }
    }

    private final Map<String, FeatureSpec> specs;

    private FeatureCatalogue(Map<String, FeatureSpec> specs) {
        this.specs = specs;
    }

    public static FeatureCatalogue of(FeatureSpec... specs) {
        Map<String, FeatureSpec> byName = new LinkedHashMap<>();
        for (FeatureSpec spec : specs) {
            if (byName.putIfAbsent(spec.name(), spec) != null) {
                throw new IllegalArgumentException("feature declared twice: " + spec.name());
            }
        }
        return new FeatureCatalogue(Collections.unmodifiableMap(byName));
    }

    public boolean contains(String name) {
        return specs.containsKey(name);
    }

    public Set<String> names() {
        return specs.keySet();
    }

    public Collection<FeatureSpec> all() {
        return specs.values();
    }
}
