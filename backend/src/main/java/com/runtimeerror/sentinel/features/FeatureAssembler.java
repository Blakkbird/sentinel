package com.runtimeerror.sentinel.features;

import com.runtimeerror.sentinel.engine.FeatureVector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Builds the feature vector for one transaction.
 *
 * Every slow or failure-prone call in a decision (all the Redis traffic) happens inside
 * this class. Once assemble() returns, the rest of the decision is pure computation.
 * Keeping all I/O in one place is what makes the latency budget manageable: there is
 * exactly one place to look when a decision is slow.
 */
@Component
public class FeatureAssembler {

    private static final Logger log = LoggerFactory.getLogger(FeatureAssembler.class);

    private static final String CARD = "card";
    private static final String DEVICE = "device";

    private final VelocityStore velocity;

    public FeatureAssembler(VelocityStore velocity) {
        this.velocity = velocity;
    }

    public FeatureVector assemble(String cardId, String deviceId, long amountMinor, Instant now) {
        boolean hasDevice = deviceId != null && !deviceId.isBlank();
        try {
            // Record first, then read, so an attempt counts itself: the sixth attempt in
            // a minute sees a count of 6, not 5.
            velocity.record(CARD, cardId, amountMinor, now);
            if (hasDevice) {
                velocity.record(DEVICE, deviceId, amountMinor, now);
            }

            VelocityStore.Window[] card = velocity.read(CARD, cardId, now, 1, 60);
            FeatureVector.Builder features = FeatureVector.builder()
                    .put(Features.TXN_AMOUNT_MINOR, amountMinor)
                    .put(Features.SYS_VELOCITY_AVAILABLE, 1)
                    .put(Features.CARD_COUNT_1M, card[0].count())
                    .put(Features.CARD_COUNT_1H, card[1].count())
                    .put(Features.CARD_AMOUNT_1H, card[1].amountMinor());

            if (hasDevice) {
                VelocityStore.Window[] device = velocity.read(DEVICE, deviceId, now, 60);
                features.put(Features.DEVICE_COUNT_1H, device[0].count());

                // How many different cards has this device tried lately? A bot working through
                // a list of stolen card numbers uses one device and many cards.
                features.put(Features.DEVICE_DISTINCT_CARDS_1H,
                        velocity.addDistinct(DEVICE, deviceId, CARD, cardId, now));

                // Has this card been used on this device before? A new device on a card that
                // already has a usual one is a classic sign of account takeover.
                VelocityStore.Membership seen = velocity.addMember(CARD, cardId, DEVICE, deviceId);
                features.put(Features.CARD_NEW_DEVICE, seen.isNew() ? 1 : 0);
                features.put(Features.CARD_KNOWN_DEVICES, seen.size());
            }
            return features.build();

        } catch (VelocityUnavailableException e) {
            log.warn("velocity unavailable, deciding in degraded mode: {}", e.getMessage());
            // All or nothing. Rules never see a half-filled picture where some counters
            // are present and others silently missing.
            return FeatureVector.builder()
                    .put(Features.TXN_AMOUNT_MINOR, amountMinor)
                    .put(Features.SYS_VELOCITY_AVAILABLE, 0)
                    .build();
        }
    }
}
