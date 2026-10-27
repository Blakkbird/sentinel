package com.runtimeerror.sentinel.rules;

import com.runtimeerror.sentinel.engine.Condition;
import com.runtimeerror.sentinel.engine.InvalidRuleSetException;
import com.runtimeerror.sentinel.engine.Operator;
import com.runtimeerror.sentinel.engine.Outcome;
import com.runtimeerror.sentinel.engine.RuleDefinition;
import com.runtimeerror.sentinel.engine.RuleMode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Converts rules to and from their stored JSON form, "format version 1".
 *
 *   {
 *     "formatVersion": 1,
 *     "rules": [
 *       { "code": "VEL_CARD_1M",
 *         "description": "Decline a card used more than 5 times in a minute...",
 *         "mode": "ACTIVE",
 *         "proposes": "DECLINE",
 *         "reasonCode": "CARD_VELOCITY_1M",
 *         "condition": { "type": "compare", "feature": "card.txnCount.1m", "op": "GT", "value": 5 } }
 *     ]
 *   }
 *
 * A condition takes one of four shapes, one for each record in Condition:
 *
 *   { "type": "compare", "feature": "card.txnCount.1m", "op": "GT", "value": 5 }
 *   { "type": "all", "parts": [ ...conditions ] }          every part is true
 *   { "type": "any", "parts": [ ...conditions ] }          at least one part is true
 *   { "type": "not", "inner": { ...one condition } }       the inner condition is false
 *
 * The "type" field says which record to build. This is called a tagged (or discriminated)
 * union. It maps one to one onto the sealed Condition interface, it is easy for the console's
 * rule builder to produce, and an unknown tag is an obvious, nameable error.
 *
 * This class works on plain Java maps and lists, which is what Jackson produces when it reads
 * JSON into Object. It never sees JSON text or Jackson's own types. So it depends on no library
 * at all, its tests need nothing running, and turning text into maps stays Jackson's job.
 *
 * Reading is strict and patient. Strict: a field it does not know is an error, because an
 * unknown field is far more likely to be a typo ("feautre") than something meaningful. Patient:
 * it keeps going after the first problem and reports every one with its location, for example
 * "rules[2] (VEL_CARD_1M).condition.op", so whoever wrote the JSON can fix everything at once.
 *
 * Reading checks shape only: the right fields, of the right types. Meaning (is the feature
 * known, are codes unique and UPPER_SNAKE_CASE) is RuleSet.compile's job, so each kind of check
 * lives in exactly one place.
 */
public final class RuleSetJson {

    public static final int FORMAT_VERSION = 1;

    // Guard rails. Real rule sets sit far below these. They exist so that a pasted or hostile
    // document cannot make the server do unbounded work or recurse until the stack runs out.
    static final int MAX_RULES = 100;
    static final int MAX_PARTS = 20;
    static final int MAX_DEPTH = 6;
    static final int MAX_CODE_LENGTH = 64;
    static final int MAX_FEATURE_LENGTH = 128;
    static final int MAX_DESCRIPTION_LENGTH = 500;

    /** Above 2^53 a double cannot hold every whole number exactly, and neither can JavaScript. */
    private static final double MAX_SAFE_INTEGER = 9_007_199_254_740_991d;

    private static final Set<String> DOCUMENT_FIELDS = Set.of("formatVersion", "rules");
    private static final Set<String> RULE_FIELDS = Set.of("code", "description", "mode", "proposes", "reasonCode", "condition");
    private static final Set<String> COMPARE_FIELDS = Set.of("type", "feature", "op", "value");
    private static final Set<String> GROUP_FIELDS = Set.of("type", "parts");
    private static final Set<String> NOT_FIELDS = Set.of("type", "inner");

    private RuleSetJson() {
    }

    // ---- writing ----------------------------------------------------------------------

    /** The whole stored document: format version plus rules. Key order is fixed, for readable JSON. */
    public static Map<String, Object> write(List<RuleDefinition> rules) {
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("formatVersion", FORMAT_VERSION);
        List<Object> written = new ArrayList<>(rules.size());
        for (RuleDefinition rule : rules) {
            written.add(writeRule(rule));
        }
        document.put("rules", written);
        return document;
    }

    public static Map<String, Object> writeRule(RuleDefinition rule) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("code", rule.code());
        out.put("description", rule.description());
        out.put("mode", rule.mode().name());
        out.put("proposes", rule.proposes().name());
        out.put("reasonCode", rule.reasonCode());
        out.put("condition", writeCondition(rule.condition()));
        return out;
    }

    /**
     * Java 21 pattern matching over the sealed Condition: the compiler knows these four cases
     * are the only ones, so adding a fifth kind of condition fails to compile until it can be
     * written as JSON too.
     */
    public static Map<String, Object> writeCondition(Condition condition) {
        Map<String, Object> out = new LinkedHashMap<>();
        switch (condition) {
            case Condition.Compare compare -> {
                out.put("type", "compare");
                out.put("feature", compare.feature());
                out.put("op", compare.op().name());
                out.put("value", number(compare.value()));
            }
            case Condition.All all -> {
                out.put("type", "all");
                out.put("parts", writeParts(all.parts()));
            }
            case Condition.Any any -> {
                out.put("type", "any");
                out.put("parts", writeParts(any.parts()));
            }
            case Condition.Not not -> {
                out.put("type", "not");
                out.put("inner", writeCondition(not.inner()));
            }
        }
        return out;
    }

    private static List<Object> writeParts(List<Condition> parts) {
        List<Object> out = new ArrayList<>(parts.size());
        for (Condition part : parts) {
            out.add(writeCondition(part));
        }
        return out;
    }

    /** 5 rather than 5.0 when the value is whole, so stored JSON reads the way people write it. */
    private static Number number(double value) {
        if (value == Math.rint(value) && Math.abs(value) <= MAX_SAFE_INTEGER) {
            return (long) value;
        }
        return value;
    }

    // ---- reading ----------------------------------------------------------------------

    /** Reads a whole document, as stored in rule_set.definition. */
    public static List<RuleDefinition> read(Object document) {
        Reader reader = new Reader();
        List<RuleDefinition> rules = reader.document(document);
        reader.throwIfProblems();
        return rules;
    }

    /** Reads just a list of rules, for example the "rules" field of the console's save request. */
    public static List<RuleDefinition> readRules(Object rules) {
        Reader reader = new Reader();
        List<RuleDefinition> read = reader.rules(rules, "rules");
        reader.throwIfProblems();
        return read;
    }

    /**
     * One read, with its list of problems. A new Reader per call keeps the static methods above
     * safe to use from many threads at once: nothing is shared between calls.
     */
    private static final class Reader {

        private final List<String> problems = new ArrayList<>();

        void throwIfProblems() {
            if (!problems.isEmpty()) {
                throw new InvalidRuleSetException(problems);
            }
        }

        List<RuleDefinition> document(Object node) {
            Map<String, Object> document = object(node, "document");
            if (document == null) {
                return List.of();
            }
            allowOnly(document, "document", DOCUMENT_FIELDS);
            Object version = document.get("formatVersion");
            if (!(version instanceof Number number) || number.doubleValue() != FORMAT_VERSION) {
                problems.add("formatVersion: must be " + FORMAT_VERSION + ", got " + shown(version));
            }
            return rules(document.get("rules"), "rules");
        }

        List<RuleDefinition> rules(Object node, String path) {
            List<Object> list = array(node, path);
            if (list == null) {
                return List.of();
            }
            if (list.size() > MAX_RULES) {
                problems.add(path + ": at most " + MAX_RULES + " rules, got " + list.size());
                return List.of();
            }
            List<RuleDefinition> rules = new ArrayList<>(list.size());
            for (int i = 0; i < list.size(); i++) {
                RuleDefinition rule = rule(list.get(i), path + "[" + i + "]");
                if (rule != null) {
                    rules.add(rule);
                }
            }
            return rules;
        }

        RuleDefinition rule(Object node, String path) {
            Map<String, Object> rule = object(node, path);
            if (rule == null) {
                return null;
            }
            // Name the rule in every message about it when its code is readable: far easier to
            // act on "rules[2] (VEL_CARD_1M).mode" than on "rules[2].mode".
            String at = rule.get("code") instanceof String named && !named.isBlank()
                    ? path + " (" + named + ")"
                    : path;
            allowOnly(rule, at, RULE_FIELDS);

            String code = text(rule, "code", at, MAX_CODE_LENGTH);
            String description = text(rule, "description", at, MAX_DESCRIPTION_LENGTH);
            RuleMode mode = choice(rule, "mode", at, RuleMode.class);
            Outcome proposes = choice(rule, "proposes", at, Outcome.class);
            String reasonCode = text(rule, "reasonCode", at, MAX_CODE_LENGTH);
            Condition condition = condition(rule.get("condition"), at + ".condition", 1);

            if (code == null || description == null || mode == null || proposes == null
                    || reasonCode == null || condition == null) {
                return null;   // the problems are already recorded
            }
            return new RuleDefinition(code, description, condition, proposes, reasonCode, mode);
        }

        Condition condition(Object node, String path, int depth) {
            if (depth > MAX_DEPTH) {
                problems.add(path + ": conditions may nest at most " + MAX_DEPTH + " levels deep");
                return null;
            }
            Map<String, Object> condition = object(node, path);
            if (condition == null) {
                return null;
            }
            if (!(condition.get("type") instanceof String type)) {
                problems.add(path + ".type: required, one of compare, all, any, not");
                return null;
            }
            return switch (type) {
                case "compare" -> compare(condition, path);
                case "all", "any" -> group(type, condition, path, depth);
                case "not" -> negation(condition, path, depth);
                default -> {
                    problems.add(path + ".type: unknown type '" + type + "', expected compare, all, any or not");
                    yield null;
                }
            };
        }

        private Condition compare(Map<String, Object> condition, String path) {
            allowOnly(condition, path, COMPARE_FIELDS);
            String feature = text(condition, "feature", path, MAX_FEATURE_LENGTH);
            Operator op = choice(condition, "op", path, Operator.class);
            Double value = finiteNumber(condition, "value", path);
            if (feature == null || op == null || value == null) {
                return null;
            }
            return new Condition.Compare(feature, op, value);
        }

        private Condition group(String type, Map<String, Object> condition, String path, int depth) {
            allowOnly(condition, path, GROUP_FIELDS);
            List<Object> parts = array(condition.get("parts"), path + ".parts");
            if (parts == null) {
                return null;
            }
            if (parts.isEmpty()) {
                problems.add(path + ".parts: needs at least one condition");
                return null;
            }
            if (parts.size() > MAX_PARTS) {
                problems.add(path + ".parts: at most " + MAX_PARTS + " conditions, got " + parts.size());
                return null;
            }
            List<Condition> read = new ArrayList<>(parts.size());
            boolean complete = true;
            for (int i = 0; i < parts.size(); i++) {
                Condition part = condition(parts.get(i), path + ".parts[" + i + "]", depth + 1);
                if (part == null) {
                    complete = false;   // keep going: later parts may have problems worth reporting too
                } else {
                    read.add(part);
                }
            }
            if (!complete) {
                return null;
            }
            return type.equals("all") ? new Condition.All(read) : new Condition.Any(read);
        }

        private Condition negation(Map<String, Object> condition, String path, int depth) {
            allowOnly(condition, path, NOT_FIELDS);
            Condition inner = condition(condition.get("inner"), path + ".inner", depth + 1);
            return inner == null ? null : new Condition.Not(inner);
        }

        // ---- small typed readers, each recording a problem instead of throwing ----------

        @SuppressWarnings("unchecked")
        private Map<String, Object> object(Object node, String path) {
            if (node == null) {
                problems.add(path + ": required");
                return null;
            }
            if (!(node instanceof Map<?, ?> map)) {
                problems.add(path + ": must be an object { ... }");
                return null;
            }
            return (Map<String, Object>) map;
        }

        @SuppressWarnings("unchecked")
        private List<Object> array(Object node, String path) {
            if (node == null) {
                problems.add(path + ": required");
                return null;
            }
            if (!(node instanceof List<?> list)) {
                problems.add(path + ": must be a list [ ... ]");
                return null;
            }
            return (List<Object>) list;
        }

        private void allowOnly(Map<String, Object> map, String path, Set<String> allowed) {
            for (String key : map.keySet()) {
                if (!allowed.contains(key)) {
                    problems.add(path + "." + key + ": unknown field");
                }
            }
        }

        private String text(Map<String, Object> map, String field, String path, int maxLength) {
            Object value = map.get(field);
            if (value == null) {
                problems.add(path + "." + field + ": required");
                return null;
            }
            if (!(value instanceof String text)) {
                problems.add(path + "." + field + ": must be text");
                return null;
            }
            if (text.isBlank()) {
                problems.add(path + "." + field + ": must not be empty");
                return null;
            }
            if (text.length() > maxLength) {
                problems.add(path + "." + field + ": at most " + maxLength + " characters, got " + text.length());
                return null;
            }
            return text;
        }

        private <E extends Enum<E>> E choice(Map<String, Object> map, String field, String path, Class<E> type) {
            Object value = map.get(field);
            String allowed = Arrays.stream(type.getEnumConstants()).map(Enum::name).collect(Collectors.joining(", "));
            if (!(value instanceof String name)) {
                problems.add(path + "." + field + ": required, one of " + allowed);
                return null;
            }
            try {
                return Enum.valueOf(type, name);
            } catch (IllegalArgumentException e) {
                problems.add(path + "." + field + ": must be one of " + allowed + ", got '" + name + "'");
                return null;
            }
        }

        private Double finiteNumber(Map<String, Object> map, String field, String path) {
            Object value = map.get(field);
            if (!(value instanceof Number number)) {
                problems.add(path + "." + field + ": required, a number");
                return null;
            }
            double d = number.doubleValue();
            if (!Double.isFinite(d)) {
                problems.add(path + "." + field + ": must be a finite number");
                return null;
            }
            return d;
        }

        private static String shown(Object value) {
            return value == null ? "nothing" : "'" + value + "'";
        }
    }
}
