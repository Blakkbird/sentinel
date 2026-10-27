package com.runtimeerror.sentinel.console;

import com.runtimeerror.sentinel.engine.Outcome;
import com.runtimeerror.sentinel.engine.RuleDefinition;
import com.runtimeerror.sentinel.engine.RuleMode;
import com.runtimeerror.sentinel.rules.RuleSetActivation;
import com.runtimeerror.sentinel.rules.RuleSetJson;
import com.runtimeerror.sentinel.rules.RuleSetNotFoundException;
import com.runtimeerror.sentinel.rules.RuleSetStatus;
import com.runtimeerror.sentinel.rules.RuleSetStore;
import com.runtimeerror.sentinel.rules.StoredRuleSet;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * What the console's Rules pages show, built from the rule_set and rule_set_activation tables.
 *
 * The records below are the JSON the console receives; field names match console/src/lib/types.ts.
 */
@Component
public class RuleSetViews {

    /** One version in a list. "live" means it is the version deciding payments right now. */
    public record Summary(
            int version,
            RuleSetStatus status,
            String title,
            Integer basedOn,
            int ruleCount,
            String createdBy,
            String createdByName,
            Instant createdAt,
            Instant updatedAt,
            Instant submittedAt,
            String reviewedByName,
            Instant reviewedAt,
            boolean live
    ) {
    }

    /** Every version, newest first, plus which is live and which was live before it. */
    public record Listing(int liveVersion, Integer previousVersion, List<Summary> items) {
    }

    /**
     * One rule, in two forms: the condition as a tree (the stored JSON shape, which the rule
     * builder edits) and as one line of text (card.txnCount.1m > 5) for reading.
     */
    public record Rule(
            String code,
            String description,
            RuleMode mode,
            Outcome proposes,
            String reasonCode,
            Map<String, Object> condition,
            String conditionText
    ) {
        static Rule of(RuleDefinition d) {
            return new Rule(d.code(), d.description(), d.mode(), d.proposes(), d.reasonCode(),
                    RuleSetJson.writeCondition(d.condition()), d.condition().describe());
        }
    }

    /** Everything about one version. */
    public record Detail(
            int version,
            RuleSetStatus status,
            String title,
            Integer basedOn,
            int revision,
            String createdBy,
            String createdByName,
            Instant createdAt,
            Instant updatedAt,
            Instant submittedAt,
            String reviewedBy,
            String reviewedByName,
            Instant reviewedAt,
            String reviewNote,
            boolean live,
            int liveVersion,
            List<Rule> rules,
            List<RuleSetActivation> activations
    ) {
    }

    private final RuleSetStore store;

    public RuleSetViews(RuleSetStore store) {
        this.store = store;
    }

    public Listing list() {
        // V3 seeds two activations, so there is always at least one.
        List<RuleSetActivation> recent = store.recentActivations(2);
        int live = recent.get(0).version();
        Integer previous = recent.size() > 1 ? recent.get(1).version() : null;
        List<Summary> items = store.findAll().stream().map(ruleSet -> summary(ruleSet, live)).toList();
        return new Listing(live, previous, items);
    }

    public Detail detail(int version) {
        StoredRuleSet rs = store.find(version).orElseThrow(() -> new RuleSetNotFoundException(version));
        int live = liveVersion();
        return new Detail(rs.version(), rs.status(), rs.title(), rs.basedOn(), rs.revision(),
                rs.createdBy(), rs.createdByName(), rs.createdAt(), rs.updatedAt(), rs.submittedAt(),
                rs.reviewedBy(), rs.reviewedByName(), rs.reviewedAt(), rs.reviewNote(),
                rs.version() == live, live,
                rs.rules().stream().map(Rule::of).toList(),
                store.activationsOf(version));
    }

    int liveVersion() {
        return store.latestActivation().map(RuleSetActivation::version).orElseThrow();
    }

    static Summary summary(StoredRuleSet rs, int liveVersion) {
        return new Summary(rs.version(), rs.status(), rs.title(), rs.basedOn(), rs.rules().size(),
                rs.createdBy(), rs.createdByName(), rs.createdAt(), rs.updatedAt(), rs.submittedAt(),
                rs.reviewedByName(), rs.reviewedAt(), rs.version() == liveVersion);
    }
}
