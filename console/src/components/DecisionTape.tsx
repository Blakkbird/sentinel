import { useNavigate } from "react-router";
import type { DecisionSummary, Outcome } from "@/lib/types";
import { formatClock, formatMoney } from "@/lib/format";
import { OUTCOME, OUTCOMES } from "@/lib/outcomes";
import { cn } from "@/lib/utils";

/**
 * The decision tape: one thin bar per decision, oldest on the left, newest arriving on the
 * right. Colour is the outcome and height is the amount (on a log scale, so a ₹49 test
 * payment and a ₹75,000 laptop both stay visible). An attack shows up as a band of red.
 */
export function DecisionTape({ items, capacity = 160 }: { items: DecisionSummary[]; capacity?: number }) {
  const navigate = useNavigate();
  const visible = items.slice(0, capacity).reverse();

  const counts: Record<Outcome, number> = { APPROVE: 0, STEP_UP: 0, DECLINE: 0 };
  let shadowDiffers = 0;
  for (const d of visible) {
    counts[d.outcome] += 1;
    if (d.shadowOutcome !== d.outcome) shadowDiffers += 1;
  }

  return (
    <section aria-labelledby="tape-label" className="space-y-2">
      <div className="relative h-[72px] rounded-md border bg-card">
        {visible.length === 0 ? (
          <p className="absolute inset-0 grid place-items-center text-[13px] text-muted-foreground">
            Waiting for the first decision
          </p>
        ) : (
          <div aria-hidden="true" className="absolute inset-x-3 top-3 bottom-3 flex items-end justify-end gap-[2px] overflow-hidden">
            {visible.map((d) => (
              <span
                key={d.decisionId}
                onClick={() => navigate(`/decisions/${encodeURIComponent(d.transactionId)}`)}
                title={`${OUTCOME[d.outcome].label}, ${formatMoney(d.amountMinor, d.currency)}, ${d.cardId}, ${formatClock(d.decidedAt)}`}
                className={cn("w-[4px] shrink-0 cursor-pointer rounded-[1px] hover:opacity-70", OUTCOME[d.outcome].bg)}
                style={{
                  height: `${barHeight(d.amountMinor)}%`,
                  // a violet cap marks a decision a shadow rule would have changed
                  boxShadow: d.shadowOutcome !== d.outcome ? "inset 0 4px 0 var(--shadow)" : undefined,
                }}
              />
            ))}
          </div>
        )}
      </div>

      <div className="flex flex-wrap items-center gap-x-5 gap-y-1 text-[13px]">
        <span id="tape-label" className="text-muted-foreground">
          Last {visible.length} {visible.length === 1 ? "decision" : "decisions"}
        </span>
        {OUTCOMES.map((outcome) => (
          <span key={outcome} className="inline-flex items-center gap-1.5">
            <span className={cn("size-2.5 rounded-[2px]", OUTCOME[outcome].bg)} aria-hidden="true" />
            <span className="font-medium tabular">{counts[outcome]}</span>
            <span className="text-muted-foreground">{OUTCOME[outcome].label.toLowerCase()}</span>
          </span>
        ))}
        {shadowDiffers > 0 && (
          <span className="inline-flex items-center gap-1.5 text-shadow">
            <span className="size-2.5 rounded-[2px] bg-shadow" aria-hidden="true" />
            <span className="font-medium tabular">{shadowDiffers}</span>
            <span>a shadow rule would change (violet cap)</span>
          </span>
        )}
      </div>
    </section>
  );
}

/** 18% for ₹1, rising with the number of digits, 100% from ₹1,00,000 up. */
function barHeight(amountMinor: number): number {
  const rupees = Math.max(1, amountMinor / 100);
  const scaled = Math.min(1, Math.log10(rupees) / 5);
  return 18 + 82 * scaled;
}
