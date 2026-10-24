package com.runtimeerror.sentinel.console;

import com.runtimeerror.sentinel.api.BadRequestException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DecisionCursorTest {

    @Test
    void survivesTheRoundTrip() {
        DecisionCursor cursor = new DecisionCursor(Instant.parse("2026-10-23T10:15:30.123456Z"),
                UUID.fromString("5b0c2d1e-7a44-4c1b-9d5e-2f1a0c9e8b7d"));

        assertThat(DecisionCursor.decode(cursor.encode())).isEqualTo(cursor);
    }

    @Test
    void isSafeToPutInAUrl() {
        String token = new DecisionCursor(Instant.now(), UUID.randomUUID()).encode();

        assertThat(token).matches("[A-Za-z0-9_-]+");
    }

    @Test
    void anEditedOrGarbledCursorIsABadRequest() {
        assertThatThrownBy(() -> DecisionCursor.decode("%%%")).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> DecisionCursor.decode("bm90LWEtY3Vyc29y")).isInstanceOf(BadRequestException.class);
    }
}
