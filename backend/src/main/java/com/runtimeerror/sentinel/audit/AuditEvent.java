package com.runtimeerror.sentinel.audit;

import java.time.Instant;
import java.util.Map;

/**
 * One row of the audit log. action is kept as text rather than as AuditAction, so an old row
 * stays readable even if the enum changes later.
 */
public record AuditEvent(
        long id,
        Instant occurredAt,
        String actor,
        String actorName,
        String action,
        String targetType,
        String targetId,
        String summary,
        Map<String, Object> details
) {
}
