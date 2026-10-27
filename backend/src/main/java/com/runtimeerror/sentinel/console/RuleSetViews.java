package com.runtimeerror.sentinel.console;

import com.runtimeerror.sentinel.engine.Outcome;
import com.runtimeerror.sentinel.engine.RuleDefinition;
import com.runtimeerror.sentinel.engine.RuleMode;
import com.runtimeerror.sentinel.rules.RuleSetActivation;
import com.runtimeerror.sentinel.rules.RuleSetJson;
import com.runtimeerror.sentinel.rules.RuleSetNotFoundException;
import com.runtimeerror.sentinel.rules.RuleSetService;
import com.runtimeerror.sentinel.rules.RuleSetStatus;
import com.runtimeerror.sentinel.rules.RuleSetStore;
import com.runtimeerror.sentinel.rules.StoredRuleSet;
import com.runtimeerror.sentinel.security.Actor;
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

    /**
     * Everything about one version, for one person: "permissions" says which actions this
     * person may take on it now, and why not where they may not.
     */
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
            List<RuleSetActivation> activations,
            List<RuleSetService.Permission> permissions
    ) {
    }

    private final RuleSetStore store;
    private final RuleSetService service;

    public RuleSetViews(RuleSetStore store, RuleSetService service) {
        this.store = store;
        this.service = service;
    }

    public Listing list() {
        // V3 seeds two activations, so there is always at least one.
        List<RuleSetActivation> recent = store.recentActivations(2);
        int live = recent.get(0).version();
        Integer previous = recent.size() > 1 ? recent.get(1).version() : null;
        List<Summary> items = store.findAll().stream().map(ruleSet -> summary(ruleSet, live)).toList();
        return new Listing(live, previous, items);
    }

    public Detail detail(int version, Actor viewer) {
        StoredRuleSet rs = store.find(version).orElseThrow(() -> new RuleSetNotFoundException(version));
        int live = liveVersion();
        return new Detail(rs.version(), rs.status(), rs.title(), rs.basedOn(), rs.revision(),
                rs.createdBy(), rs.createdByName(), rs.createdAt(), rs.updatedAt(), rs.submittedAt(),
                rs.reviewedBy(), rs.reviewedByName(), rs.reviewedAt(), rs.reviewNote(),
                rs.version() == live, live,
                rs.rules().stream().map(Rule::of).toList(),
                store.activationsOf(version),
                service.permissions(rs, viewer));
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
