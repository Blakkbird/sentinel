package com.runtimeerror.sentinel.console;

import com.runtimeerror.sentinel.api.BadRequestException;

import java.nio.charset.StandardCharsets;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

/**
 * A bookmark for "where the last page ended". This is keyset pagination.
 *
 * Plain version: the usual way to page, "skip the first 500 rows" (OFFSET 500), makes the
 * database read and throw away 500 rows, and gets slower the further back you go. Instead,
 * the next page asks for "decisions older than the last one I was shown". The index on
 * decided_at takes the database straight there, however far back that is.
 *
 * Two decisions can share a timestamp, so the bookmark also holds the decision id as a
 * tie-breaker. It is base64-encoded so callers treat it as an opaque token: something to
 * hand back, never something to build or edit.
 */
public record DecisionCursor(Instant decidedAt, UUID decisionId) {

    public String encode() {
        String raw = decidedAt + "|" + decisionId;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    public static DecisionCursor decode(String token) {
        try {
            String raw = new String(Base64.getUrlDecoder().decode(token), StandardCharsets.UTF_8);
            int bar = raw.indexOf('|');
            if (bar < 0) {
                throw invalid();
            }
            return new DecisionCursor(Instant.parse(raw.substring(0, bar)), UUID.fromString(raw.substring(bar + 1)));
        } catch (IllegalArgumentException | DateTimeException e) {
            throw invalid();
        }
    }

    private static BadRequestException invalid() {
        return new BadRequestException("The page cursor is not valid. Start again from the first page.");
    }
}
