package com.runtimeerror.sentinel.console;

import com.runtimeerror.sentinel.api.DecisionNotFoundException;
import com.runtimeerror.sentinel.decision.DecisionService;
import com.runtimeerror.sentinel.engine.Outcome;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The console's view of decisions. Read-only, and only for people signed in (SecurityConfig).
 *
 *   GET /api/decisions?q=card-42&outcome=DECLINE&degraded=true&shadowChanges=true&cursor=...&limit=50
 *   GET /api/decisions/{transactionId}
 */
@RestController
@RequestMapping("/api/decisions")
public class ConsoleDecisionController {

    private static final int MAX_LIMIT = 200;

    private final DecisionQueries queries;
    private final DecisionService decisions;
    private final DecisionViews views;

    public ConsoleDecisionController(DecisionQueries queries, DecisionService decisions, DecisionViews views) {
        this.queries = queries;
        this.decisions = decisions;
        this.views = views;
    }

    @GetMapping
    public DecisionPage search(@RequestParam(required = false) Outcome outcome,
                               @RequestParam(required = false) String q,
                               @RequestParam(defaultValue = "false") boolean degraded,
                               @RequestParam(defaultValue = "false") boolean shadowChanges,
                               @RequestParam(required = false) String cursor,
                               @RequestParam(defaultValue = "50") int limit) {
        DecisionFilter filter = new DecisionFilter(outcome, q, degraded, shadowChanges);
        DecisionCursor after = cursor == null || cursor.isBlank() ? null : DecisionCursor.decode(cursor);
        return queries.search(filter, after, Math.max(1, Math.min(limit, MAX_LIMIT)));
    }

    @GetMapping("/{transactionId}")
    public DecisionView detail(@PathVariable String transactionId) {
        return decisions.find(transactionId)
                .map(views::of)
                .orElseThrow(() -> new DecisionNotFoundException(transactionId));
    }
}
