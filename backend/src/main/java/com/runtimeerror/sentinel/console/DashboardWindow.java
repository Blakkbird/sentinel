package com.runtimeerror.sentinel.console;

import com.runtimeerror.sentinel.api.BadRequestException;

import java.time.Duration;
import java.time.Instant;

/**
 * How far back the dashboard looks, and how finely it slices that time for the chart.
 *
 *   15m  15 buckets of one minute
 *   1h   60 buckets of one minute
 *   24h  96 buckets of fifteen minutes
 *
 * Only these three exist on purpose. A free-form "from" and "to" would let one request ask
 * for a year of decisions in one-minute slices, and the page would have to draw whatever came back.
 */
public enum DashboardWindow {

    LAST_15_MINUTES("15m", Duration.ofMinutes(15), Duration.ofMinutes(1)),
    LAST_HOUR("1h", Duration.ofHours(1), Duration.ofMinutes(1)),
    LAST_24_HOURS("24h", Duration.ofHours(24), Duration.ofMinutes(15));

    private final String label;
    private final Duration length;
    private final Duration bucket;

    DashboardWindow(String label, Duration length, Duration bucket) {
        this.label = label;
        this.length = length;
        this.bucket = bucket;
    }

    /** The text used in the address: 15m, 1h or 24h. */
    public String label() {
        return label;
    }

    public Duration bucket() {
        return bucket;
    }

    public int buckets() {
        return (int) (length.toSeconds() / bucket.toSeconds());
    }

    /**
     * The start of the first bucket (inclusive).
     *
     * Buckets sit on round clock times (10:14:00, not 10:14:37), so a bar in the chart means the
     * same minute on every refresh instead of sliding along with the second hand. The bucket
     * that contains "now" is the last one and is still filling up; the window is that bucket
     * plus the ones before it.
     */
    public Instant from(Instant now) {
        return to(now).minus(length);
    }

    /** The end of the last bucket (exclusive): the next round clock time after "now". */
    public Instant to(Instant now) {
        long size = bucket.toSeconds();
        long currentBucket = Math.floorDiv(now.getEpochSecond(), size) * size;
        return Instant.ofEpochSecond(currentBucket + size);
    }

    /** Reads ?window=...; anything other than the three known values is a 400. */
    public static DashboardWindow parse(String text) {
        for (DashboardWindow window : values()) {
            if (window.label.equals(text)) {
                return window;
            }
        }
        throw new BadRequestException("'" + text + "' is not a valid value for window. Use 15m, 1h or 24h.");
    }
}
