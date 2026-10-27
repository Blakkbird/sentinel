import { formatCount, formatMoney } from "./format";

/**
 * Short labels for the facts the rules look at. The backend sends the technical name
 * (card.txnCount.1m) and a long description; the console shows these labels instead.
 * A feature missing from this list still displays, under its technical name.
 */
export type FeatureKind = "money" | "count" | "flag";

const FEATURES: Record<string, { label: string; kind: FeatureKind }> = {
  "txn.amountMinor": { label: "Payment amount", kind: "money" },
  "sys.velocityAvailable": { label: "Counters available", kind: "flag" },
  "card.txnCount.1m": { label: "Card attempts this minute", kind: "count" },
  "card.txnCount.1h": { label: "Card attempts in the last hour", kind: "count" },
  "card.amountSum.1h": { label: "Card spend in the last hour", kind: "money" },
  "device.txnCount.1h": { label: "Device attempts in the last hour", kind: "count" },
  "device.distinctCards.1h": { label: "Different cards on this device, last hour", kind: "count" },
  "card.newDevice": { label: "New device for this card", kind: "flag" },
  "card.knownDevices": { label: "Devices this card has used", kind: "count" },
};

export function featureLabel(name: string): string {
  return FEATURES[name]?.label ?? name;
}

/** money (paise), count, or flag (1 = yes, 0 = no). Unknown features are treated as counts. */
export function featureKind(name: string): FeatureKind {
  return FEATURES[name]?.kind ?? "count";
}

export function formatFeatureValue(name: string, value: number, currency = "INR"): string {
  const kind = featureKind(name);
  if (kind === "money") return formatMoney(value, currency);
  if (kind === "flag") return value === 1 ? "Yes" : "No";
  return Number.isInteger(value) ? formatCount(value) : String(value);
}
