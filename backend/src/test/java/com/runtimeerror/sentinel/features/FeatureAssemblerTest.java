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
