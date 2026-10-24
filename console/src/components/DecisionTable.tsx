import { Link, useNavigate } from "react-router";
import type { DecisionSummary } from "@/lib/types";
import { formatClock, formatMoney } from "@/lib/format";
import { OUTCOME } from "@/lib/outcomes";
import { OutcomeBadge } from "./OutcomeBadge";
import { Ago } from "./Time";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { cn } from "@/lib/utils";

/** A list of decisions. Used by the live feed and by the search page. */
export function DecisionTable({
  items,
  now,
  arrivedLive,
}: {
  items: DecisionSummary[];
  now: number;
  /** Ids of rows that arrived over the live feed, which get a brief highlight. */
  arrivedLive?: Set<string>;
}) {
  const navigate = useNavigate();

  return (
    <div className="rounded-md border bg-card">
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead className="w-[118px]">When</TableHead>
            <TableHead>Transaction</TableHead>
            <TableHead>Card</TableHead>
            <TableHead className="hidden lg:table-cell">Device</TableHead>
            <TableHead className="hidden xl:table-cell">Merchant</TableHead>
            <TableHead className="text-right">Amount</TableHead>
            <TableHead>Outcome</TableHead>
            <TableHead className="hidden md:table-cell">Why</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {items.map((d) => {
            const href = `/decisions/${encodeURIComponent(d.transactionId)}`;
            const shadowDiffers = d.shadowOutcome !== d.outcome;
            return (
              <TableRow
                key={d.decisionId}
                onClick={() => navigate(href)}
                className={cn(
                  "cursor-pointer transition-colors hover:bg-muted/50",
                  arrivedLive?.has(d.decisionId) && "animate-arrive",
                )}
              >
                <TableCell className="whitespace-nowrap">
                  <span className="tabular">{formatClock(d.decidedAt)}</span>
                  <span className="ml-2 text-[12px] text-muted-foreground">
                    <Ago iso={d.decidedAt} now={now} />
                  </span>
                </TableCell>
                <TableCell>
                  <Link
                    to={href}
                    onClick={(e) => e.stopPropagation()}
                    className="block max-w-[18ch] truncate font-mono text-[12.5px] hover:underline"
                    title={d.transactionId}
                  >
                    {d.transactionId}
                  </Link>
                </TableCell>
                <TableCell>
                  <span className="block max-w-[18ch] truncate font-mono text-[12.5px]" title={d.cardId}>
                    {d.cardId}
                  </span>
                </TableCell>
                <TableCell className="hidden lg:table-cell">
                  <span className="block max-w-[16ch] truncate font-mono text-[12.5px] text-muted-foreground" title={d.deviceId ?? ""}>
                    {d.deviceId ?? "None"}
                  </span>
                </TableCell>
                <TableCell className="hidden xl:table-cell">
                  <span className="block max-w-[16ch] truncate font-mono text-[12.5px] text-muted-foreground" title={d.merchantId ?? ""}>
                    {d.merchantId ?? "None"}
                  </span>
                </TableCell>
                <TableCell className="text-right whitespace-nowrap tabular">{formatMoney(d.amountMinor, d.currency)}</TableCell>
                <TableCell>
                  <OutcomeBadge outcome={d.outcome} />
                </TableCell>
                <TableCell className="hidden max-w-[32ch] md:table-cell">
                  <div className="flex flex-wrap items-center gap-x-2 gap-y-0.5 text-[12.5px]">
                    {d.reasonCodes.length > 0 ? (
                      <span className="truncate text-muted-foreground" title={d.reasonCodes.join(", ")}>
                        {d.reasonCodes.join(", ")}
                      </span>
                    ) : (
                      !shadowDiffers && !d.degraded && <span className="text-muted-foreground/70">No rule fired</span>
                    )}
                    {shadowDiffers && (
                      <span className="text-shadow" title="What the shadow rules would have decided">
                        Shadow: {OUTCOME[d.shadowOutcome].label.toLowerCase()}
                      </span>
                    )}
                    {d.degraded && <span className="text-stepup">Degraded</span>}
                  </div>
                </TableCell>
              </TableRow>
            );
          })}
        </TableBody>
      </Table>
    </div>
  );
}
