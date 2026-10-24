package com.runtimeerror.sentinel.features;

import com.runtimeerror.sentinel.engine.FeatureVector;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class FeatureAssemblerTest {

    private final InMemoryVelocityStore store = new InMemoryVelocityStore();
    private final FeatureAssembler assembler = new FeatureAssembler(store);
    private final Instant now = Instant.parse("2026-01-01T10:00:30Z");

    @Test
    void producesExactlyTheCataloguedFeatures() {
        FeatureVector features = assembler.assemble("card-1", "dev-1", 100_00, now);

        // If this fails, someone added a feature to the assembler without cataloguing it,
        // or catalogued one the assembler never produces.
        assertThat(features.asMap().keySet())
                .containsExactlyInAnyOrderElementsOf(Features.CATALOGUE.names());
    }

    @Test
    void countsIncludeTheCurrentAttempt() {
        assembler.assemble("card-1", null, 100_00, now);
        assembler.assemble("card-1", null, 200_00, now);
        FeatureVector third = assembler.assemble("card-1", null, 300_00, now);

        assertThat(third.get(Features.CARD_COUNT_1M)).isEqualTo(3);
        assertThat(third.get(Features.CARD_AMOUNT_1H)).isEqualTo(600_00);
    }

    @Test
    void olderAttemptsLeaveTheOneMinuteWindowButStayInTheHour() {
        assembler.assemble("card-1", null, 100_00, now);
        FeatureVector later = assembler.assemble("card-1", null, 100_00, now.plus(Duration.ofMinutes(2)));

        assertThat(later.get(Features.CARD_COUNT_1M)).isEqualTo(1);
        assertThat(later.get(Features.CARD_COUNT_1H)).isEqualTo(2);
    }

    @Test
    void deviceFeaturesAreAbsentWhenNoDeviceIsSent() {
        FeatureVector features = assembler.assemble("card-1", " ", 100_00, now);

        assertThat(features.has(Features.DEVICE_COUNT_1H)).isFalse();
        assertThat(features.has(Features.DEVICE_DISTINCT_CARDS_1H)).isFalse();
        assertThat(features.has(Features.CARD_NEW_DEVICE)).isFalse();
        assertThat(features.has(Features.CARD_KNOWN_DEVICES)).isFalse();
    }

    @Test
    void countsTheDifferentCardsADeviceHasTried() {
        assembler.assemble("card-1", "dev-1", 100_00, now);
        assembler.assemble("card-2", "dev-1", 100_00, now);
        assembler.assemble("card-2", "dev-1", 100_00, now);   // a repeat card does not count twice
        FeatureVector fourth = assembler.assemble("card-3", "dev-1", 100_00, now);

        assertThat(fourth.get(Features.DEVICE_DISTINCT_CARDS_1H)).isEqualTo(3);
    }

    @Test
    void flagsTheFirstTimeACardIsSeenOnADevice() {
        FeatureVector first = assembler.assemble("card-1", "dev-1", 100_00, now);
        FeatureVector again = assembler.assemble("card-1", "dev-1", 100_00, now);
        FeatureVector elsewhere = assembler.assemble("card-1", "dev-2", 100_00, now);

        assertThat(first.get(Features.CARD_NEW_DEVICE)).isEqualTo(1);
        assertThat(first.get(Features.CARD_KNOWN_DEVICES)).isEqualTo(1);
        assertThat(again.get(Features.CARD_NEW_DEVICE)).isEqualTo(0);
        assertThat(elsewhere.get(Features.CARD_NEW_DEVICE)).isEqualTo(1);
        assertThat(elsewhere.get(Features.CARD_KNOWN_DEVICES)).isEqualTo(2);
    }

    @Test
    void anOutageGivesADegradedVectorWithNoVelocityAtAll() {
        store.setAvailable(false);

        FeatureVector features = assembler.assemble("card-1", "dev-1", 100_00, now);

        assertThat(features.get(Features.SYS_VELOCITY_AVAILABLE)).isEqualTo(0);
        assertThat(features.asMap()).containsOnlyKeys(
                Features.TXN_AMOUNT_MINOR, Features.SYS_VELOCITY_AVAILABLE);
    }
}
