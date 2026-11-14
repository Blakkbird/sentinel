package com.runtimeerror.sentinel.console;

import com.runtimeerror.sentinel.api.BadRequestException;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DashboardWindowTest {

    private static final Instant NOW = Instant.parse("2026-01-01T10:00:30Z");

    @Test
    void theLastHourIsTheCurrentMinuteAndThe59BeforeIt() {
        DashboardWindow window = DashboardWindow.parse("1h");

        assertThat(window.from(NOW)).isEqualTo(Instant.parse("2026-01-01T09:01:00Z"));
        assertThat(window.to(NOW)).isEqualTo(Instant.parse("2026-01-01T10:01:00Z"));
        assertThat(window.buckets()).isEqualTo(60);
    }

    @Test
    void fifteenMinutesHasFifteenOneMinuteBuckets() {
        DashboardWindow window = DashboardWindow.parse("15m");

        assertThat(window.from(NOW)).isEqualTo(Instant.parse("2026-01-01T09:46:00Z"));
        assertThat(window.to(NOW)).isEqualTo(Instant.parse("2026-01-01T10:01:00Z"));
        assertThat(window.buckets()).isEqualTo(15);
    }

    @Test
    void aDayIsCutIntoQuarterHoursOnTheQuarterHour() {
        DashboardWindow window = DashboardWindow.parse("24h");

        assertThat(window.from(NOW)).isEqualTo(Instant.parse("2025-12-31T10:15:00Z"));
        assertThat(window.to(NOW)).isEqualTo(Instant.parse("2026-01-01T10:15:00Z"));
        assertThat(window.buckets()).isEqualTo(96);
    }

    @Test
    void aMomentExactlyOnABoundaryStartsANewBucket() {
        Instant onTheMinute = Instant.parse("2026-01-01T10:00:00Z");

        assertThat(DashboardWindow.LAST_HOUR.to(onTheMinute)).isEqualTo(Instant.parse("2026-01-01T10:01:00Z"));
    }

    @Test
    void anythingElseIsRefused() {
        assertThatThrownBy(() -> DashboardWindow.parse("7d"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("15m, 1h or 24h");
        assertThatThrownBy(() -> DashboardWindow.parse("1H")).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> DashboardWindow.parse("")).isInstanceOf(BadRequestException.class);
    }
}
