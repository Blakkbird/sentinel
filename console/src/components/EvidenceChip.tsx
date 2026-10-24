import { Check, X } from "lucide-react";
import type { Evidence } from "@/lib/types";
import { featureLabel, formatFeatureValue } from "@/lib/features";
import { OPERATOR_SYMBOL } from "@/lib/outcomes";
import { cn } from "@/lib/utils";

const FLAGS = new Set(["card.newDevice", "sys.velocityAvailable"]);

/**
 * One comparison from a rule, checked against what the rule actually saw:
 *   ✓ Card attempts this minute  7 > 5
 * A missing value means the fact was not available, so the rule could not run.
 */
export function EvidenceChip({ evidence, currency }: { evidence: Evidence; currency: string }) {
  const { feature, op, threshold, actual, holds } = evidence;
  const missing = actual === null;
  const isFlag = FLAGS.has(feature);

  const condition = `${feature} ${OPERATOR_SYMBOL[op]} ${threshold}`;
  const verdict = missing ? "not available, so the rule could not run" : holds ? "met" : "not met";

  return (
    <span
      title={`${condition}: ${verdict}`}
      className={cn(
        "inline-flex items-center gap-1.5 rounded-md border px-2 py-1 text-[12.5px] leading-none",
        holds ? "border-foreground/25 bg-card" : "border-border bg-muted/40 text-muted-foreground",
      )}
    >
      {holds ? (
        <Check className="size-3.5 shrink-0" aria-label="true" />
      ) : (
        <X className="size-3.5 shrink-0" aria-label={missing ? "not available" : "false"} />
      )}
      <span>{featureLabel(feature)}</span>
      {missing ? (
        <span className="italic">not available</span>
      ) : isFlag ? (
        <span className="font-medium">{formatFeatureValue(feature, actual, currency)}</span>
      ) : (
        <span className="tabular">
          <span className="font-semibold">{formatFeatureValue(feature, actual, currency)}</span>{" "}
          <span className="text-muted-foreground">
            {OPERATOR_SYMBOL[op]} {formatFeatureValue(feature, threshold, currency)}
          </span>
        </span>
      )}
    </span>
  );
}
