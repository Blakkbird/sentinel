package com.runtimeerror.sentinel.console;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Rule set versions, for the console. Signed-in users only (SecurityConfig).
 *
 *   GET /api/rule-sets              every version, newest first, and which one is live
 *   GET /api/rule-sets/{version}    one version: its rules, who wrote and reviewed it, when it was live
 */
@RestController
@RequestMapping("/api/rule-sets")
public class RuleSetController {

    private final RuleSetViews views;

    public RuleSetController(RuleSetViews views) {
        this.views = views;
    }

    @GetMapping
    public RuleSetViews.Listing list() {
        return views.list();
    }

    @GetMapping("/{version}")
    public RuleSetViews.Detail detail(@PathVariable int version) {
        return views.detail(version);
    }
}
