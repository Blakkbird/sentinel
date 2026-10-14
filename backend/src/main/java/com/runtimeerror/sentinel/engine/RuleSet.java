package com.runtimeerror.sentinel.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

/**
 * A checked, numbered, unchangeable collection of rules, ready to run.
 *
 * Two ideas live here.
 *
 * Compile once, run many times. All the checking (valid codes, known features, no
 * duplicates) and all the preparation (working out which features each rule needs)
 * happens once, in compile(). Evaluating a decision then does no checking at all.
 *
 * Immutable and versioned. A rule set is never edited. Changing a rule means compiling
 * version N+1. Every stored decision records the version that produced it, so an old
 * decision stays explainable after the rules have moved on, and rolling back means
 * pointing at an older version rather than undoing edits.
 */
public final class RuleSet {

    private static final Pattern UPPER_SNAKE = Pattern.compile("[A-Z][A-Z0-9_]*");

    /** A definition plus the precomputed set of features it reads. */
    public record CompiledRule(RuleDefinition definition, Set<String> requiredFeatures) {
    }

    private final int version;
    private final List<CompiledRule> rules;
    private final Set<String> requiredFeatures;

    private RuleSet(int version, List<CompiledRule> rules, Set<String> requiredFeatures) {
        this.version = version;
        this.rules = rules;
        this.requiredFeatures = requiredFeatures;
    }

    public static RuleSet compile(int version, List<RuleDefinition> definitions, FeatureCatalogue catalogue) {
        List<String> problems = new ArrayList<>();
        if (version < 1) {
            problems.add("version must be 1 or higher, got " + version);
        }
        if (definitions.isEmpty()) {
            problems.add("a rule set needs at least one rule");
        }

        Set<String> seenCodes = new HashSet<>();
        List<CompiledRule> compiled = new ArrayList<>(definitions.size());
        Set<String> union = new TreeSet<>();

        for (RuleDefinition def : definitions) {
            String code = def.code();
            if (!UPPER_SNAKE.matcher(code).matches()) {
                problems.add(code + ": rule codes must be UPPER_SNAKE_CASE");
            }
            if (!seenCodes.add(code)) {
                problems.add(code + ": duplicate rule code");
            }
            if (!UPPER_SNAKE.matcher(def.reasonCode()).matches()) {
                problems.add(code + ": reason codes must be UPPER_SNAKE_CASE");
            }
            if (def.proposes() == Outcome.APPROVE) {
                problems.add(code + ": a rule that proposes APPROVE can never change an outcome,"
                        + " because the most severe proposal always wins");
            }

            Set<String> needed = def.condition().features();
            for (String feature : needed) {
                if (!catalogue.contains(feature)) {
                    problems.add(code + ": unknown feature '" + feature + "'");
                }
            }

            compiled.add(new CompiledRule(def, needed));
            if (def.mode() != RuleMode.OFF) {
                union.addAll(needed);
            }
        }

        if (!problems.isEmpty()) {
            throw new InvalidRuleSetException(problems);
        }
        return new RuleSet(version, List.copyOf(compiled), Collections.unmodifiableSet(union));
    }

    public int version() {
        return version;
    }

    public List<CompiledRule> rules() {
        return rules;
    }

    /** Every feature read by any rule that is not OFF. */
    public Set<String> requiredFeatures() {
        return requiredFeatures;
    }
}
