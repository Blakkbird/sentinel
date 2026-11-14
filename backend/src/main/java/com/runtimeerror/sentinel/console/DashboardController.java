package com.runtimeerror.sentinel.console;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The dashboard: how many decisions, how they split, how long they took and which rules fired.
 * Read-only, for anyone signed in to the console (SecurityConfig covers everything under /api).
 *
 *   GET /api/dashboard?window=15m|1h|24h     (1h when left out)
 */
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardQueries queries;

    public DashboardController(DashboardQueries queries) {
        this.queries = queries;
    }

    @GetMapping
    public DashboardView dashboard(@RequestParam(defaultValue = "1h") String window) {
        return queries.summarise(DashboardWindow.parse(window));
    }
}
