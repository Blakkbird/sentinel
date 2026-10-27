package com.runtimeerror.sentinel.audit;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * GET /api/audit?before=120&limit=50: the audit log, newest first, for anyone signed in.
 * Read-only: nothing, not even an admin, can change it from here (or anywhere: see V4).
 */
@RestController
@RequestMapping("/api/audit")
public class AuditController {

    private static final int MAX_LIMIT = 200;

    private final AuditLog audit;

    public AuditController(AuditLog audit) {
        this.audit = audit;
    }

    @GetMapping
    public AuditLog.Page page(@RequestParam(required = false) Long before,
                              @RequestParam(defaultValue = "50") int limit) {
        return audit.page(before, Math.max(1, Math.min(limit, MAX_LIMIT)));
    }
}
