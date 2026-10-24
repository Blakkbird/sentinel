package com.runtimeerror.sentinel.features;

import com.runtimeerror.sentinel.engine.FeatureCatalogue;
import com.runtimeerror.sentinel.engine.FeatureCatalogue.FeatureSpec;

/**
 * The name of every feature Sentinel computes, in one place.
 *
 * Naming convention: entity.measure.window
 *   card.txnCount.1m   how many attempts this card made in the last minute
 *
 * CATALOGUE is what every rule set is checked against. Add a feature here first, then
 * compute it in FeatureAssembler, then write rules that use it. FeatureAssemblerTest
 * fails if the assembler and this list ever disagree.
 */
public final class Features {

    public static final String TXN_AMOUNT_MINOR = "txn.amountMinor";
    public static final String SYS_VELOCITY_AVAILABLE = "sys.velocityAvailable";
    public static final String CARD_COUNT_1M = "card.txnCount.1m";
    public static final String CARD_COUNT_1H = "card.txnCount.1h";
    public static final String CARD_AMOUNT_1H = "card.amountSum.1h";
    public static final String DEVICE_COUNT_1H = "device.txnCount.1h";
    public static final String DEVICE_DISTINCT_CARDS_1H = "device.distinctCards.1h";
    public static final String CARD_NEW_DEVICE = "card.newDevice";
    public static final String CARD_KNOWN_DEVICES = "card.knownDevices";

    public static final FeatureCatalogue CATALOGUE = FeatureCatalogue.of(
            new FeatureSpec(TXN_AMOUNT_MINOR,
                    "Amount of this transaction in paise (100 paise = 1 rupee)"),
            new FeatureSpec(SYS_VELOCITY_AVAILABLE,
                    "1 if the velocity counters could be read for this decision, 0 if the counter store was down"),
            new FeatureSpec(CARD_COUNT_1M,
                    "Attempts on this card in the current clock minute, including this one"),
            new FeatureSpec(CARD_COUNT_1H,
                    "Attempts on this card in the last 60 one-minute buckets (59 to 60 minutes), including this one"),
            new FeatureSpec(CARD_AMOUNT_1H,
                    "Total amount attempted on this card in the last 60 one-minute buckets, in paise, including this one"),
            new FeatureSpec(DEVICE_COUNT_1H,
                    "Attempts from this device in the last 60 one-minute buckets, including this one. Absent when no device id is sent"),
            new FeatureSpec(DEVICE_DISTINCT_CARDS_1H,
                    "Different cards used on this device in the last six ten-minute buckets (50 to 60 minutes), including this"
                            + " one. Approximate: counted with a HyperLogLog. Absent when no device id is sent"),
            new FeatureSpec(CARD_NEW_DEVICE,
                    "1 the first time this card is seen on this device, otherwise 0. Absent when no device id is sent"),
            new FeatureSpec(CARD_KNOWN_DEVICES,
                    "Different devices this card has been used from, including this one. Forgotten after 30 days without"
                            + " use. Absent when no device id is sent")
    );

    private Features() {
    }
}
