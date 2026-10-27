import { useState } from "react";
import { Link } from "react-router";
import { CircleCheck, LoaderCircle, RotateCcw, TriangleAlert } from "lucide-react";
import type { Outcome, ReplayReport, RuleSetDetail } from "@/lib/types";
import { formatClock, formatCount, formatDateTime, formatMoney } from "@/lib/format";
import { MODE_LABEL, OUTCOME, OUTCOMES } from "@/lib/outcomes";
import { useReplay } from "@/hooks/useRuleSets";
import { ReplayStrip } from "./ReplayStrip";
import { OutcomeBadge } from "@/components/OutcomeBadge";
import { EmptyState, ErrorState } from "@/components/States";
import { Button } from "@/components/ui/button";
import { NativeSelect } from "@/components/ui/select";
import { Skeleton } from "@/components/ui/skeleton";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { cn } from "@/lib/utils";

const SIZES = [200, 1000, 5000];
const PLURAL: Record<Outcome, string> = { APPROVE: "approvals", STEP_UP: "step-ups", DECLINE: "declines" };

/** Rule Lab: what this version would have done to recent payments, next to what the live rules do. */
export function RuleLabPanel({ d, unsavedEdits }: { d: RuleSetDetail; unsavedEdits: boolean }) {
  const [size, setSize] = useState(1000);
  const replay = useReplay(d.version, size);

  return (
    <section aria-label="Rule Lab" className="space-y-6">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <p className="max-w-[72ch] text-muted-foreground">
          Replays recent decisions through version {d.version} and through the live rules (version {d.liveVersion}), using
          the facts each decision recorded. It only reads: nothing is changed.
        </p>
        <div className="flex items-center gap-2">
          <NativeSelect
            aria-label="How many recent decisions to replay"
            value={String(size)}
            onChange={(e) => setSize(Number(e.target.value))}
            className="w-[200px]"
          >
            {SIZES.map((n) => (
              <option key={n} value={n}>
                Last {formatCount(n)} decisions
              </option>
            ))}
          </NativeSelect>
          <Button variant="outline" onClick={() => replay.refetch()} disabled={replay.isFetching}>
            {replay.isFetching ? <LoaderCircle className="animate-spin" aria-hidden="true" /> : <RotateCcw aria-hidden="true" />}
            Run again
          </Button>
        </div>
      </div>

      {unsavedEdits && (
        <p className="rounded-md border border-dashed px-4 py-2.5 text-[13px]">
          Rule Lab tests the saved draft. Save to include your latest changes.
        </p>
      )}

      {replay.isPending && <Skeleton className="h-72 w-full" />}
      {replay.isError && <ErrorState error={replay.error} onRetry={() => replay.refetch()} />}
      {replay.data && <ReplayResult r={replay.data} />}
    </section>
  );
}

function ReplayResult({ r }: { r: ReplayReport }) {
  if (r.decisions === 0) {
    return (
      <EmptyState title="No decisions to replay yet">
        Send some payments with the simulator (<span className="font-mono">py simulator\traffic.py normal</span>), then run
        the lab again.
      </EmptyState>
    );
  }

  const moves = r.moves.map((m) => `${formatCount(m.count)} ${PLURAL[m.from]} would become ${PLURAL[m.to]}`);
  return (
    <div className="space-y-8">
      <div className="space-y-3">
        <div>
          <p className="font-display text-[26px] leading-tight font-semibold">
            {r.changed === 0
              ? `No decision would change.`
              : `${formatCount(r.changed)} of ${formatCount(r.decisions)} decisions would change.`}
          </p>
          <p className="text-muted-foreground">
            {moves.length > 0 ? `${moves.join("; ")}. ` : ""}
            {r.from && r.to && `Decisions from ${formatDateTime(r.from)} to ${formatDateTime(r.to)}.`}
          </p>
        </div>
        <ReplayStrip live={r.liveStrip} next={r.versionStrip} />
        <p className="text-[12.5px] text-muted-foreground">
          One mark per decision, oldest on the left. Full-height marks are decisions this version would change, in the
          colour of their new outcome.
        </p>
      </div>

      <div className="grid gap-4 md:grid-cols-2">
        <OutcomeMix label={`Live rules, version ${r.liveVersion}`} counts={r.liveOutcomes} total={r.decisions} />
        <OutcomeMix label={`Version ${r.version}`} counts={r.versionOutcomes} total={r.decisions} />
      </div>

      <CheckLine check={r.check} />

      <section aria-labelledby="lab-rules" className="space-y-2">
        <h3 id="lab-rules" className="text-[15px] font-semibold">
          Rule by rule
        </h3>
        <div className="rounded-md border bg-card">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Rule</TableHead>
                <TableHead className="text-right">Fires with live rules</TableHead>
                <TableHead className="text-right">Would fire</TableHead>
                <TableHead className="text-right">Could not run</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {r.rules.map((rule) => {
                const differs = rule.liveFired !== rule.versionFired;
                return (
                  <TableRow key={rule.code} className={cn(differs && "bg-muted/40")}>
                    <TableCell>
                      <span className="font-mono text-[12.5px] font-medium">{rule.code}</span>
                      <span className={cn("ml-2 text-[12px]", rule.mode === "SHADOW" ? "text-shadow" : "text-muted-foreground")}>
                        {rule.versionFired === null ? "removed" : MODE_LABEL[rule.mode].toLowerCase()}
                      </span>
                    </TableCell>
                    <TableCell className="text-right tabular">{rule.liveFired === null ? <span className="text-muted-foreground">new</span> : formatCount(rule.liveFired)}</TableCell>
                    <TableCell className={cn("text-right tabular", differs && "font-semibold")}>
                      {rule.versionFired === null ? <span className="text-muted-foreground">removed</span> : formatCount(rule.versionFired)}
                    </TableCell>
                    <TableCell className={cn("text-right tabular", rule.versionAbstained === 0 && "text-muted-foreground")}>
                      {formatCount(rule.versionAbstained)}
                    </TableCell>
                  </TableRow>
                );
              })}
            </TableBody>
          </Table>
        </div>
        <p className="text-[12.5px] text-muted-foreground">
          “Could not run” counts decisions that did not record a fact the rule needs, such as a device id, or decisions
          made before a signal existed. A missing fact is never treated as zero.
        </p>
      </section>

      {r.examples.length > 0 && (
        <section aria-labelledby="lab-changed" className="space-y-2">
          <h3 id="lab-changed" className="text-[15px] font-semibold">
            Decisions that would change{r.changed > r.examples.length && `, the latest ${r.examples.length}`}
          </h3>
          <div className="rounded-md border bg-card">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>When</TableHead>
                  <TableHead>Transaction</TableHead>
                  <TableHead>Card</TableHead>
                  <TableHead className="text-right">Amount</TableHead>
                  <TableHead>Live rules</TableHead>
                  <TableHead>This version</TableHead>
                  <TableHead className="hidden lg:table-cell">Because of</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {r.examples.map((e) => (
                  <TableRow key={e.transactionId}>
                    <TableCell className="tabular whitespace-nowrap">{formatClock(e.decidedAt)}</TableCell>
                    <TableCell>
                      <Link
                        to={`/decisions/${encodeURIComponent(e.transactionId)}`}
                        className="block max-w-[18ch] truncate font-mono text-[12.5px] hover:underline"
                        title={e.transactionId}
                      >
                        {e.transactionId}
                      </Link>
                    </TableCell>
                    <TableCell>
                      <span className="block max-w-[16ch] truncate font-mono text-[12.5px]" title={e.cardId}>
                        {e.cardId}
                      </span>
                    </TableCell>
                    <TableCell className="text-right whitespace-nowrap tabular">{formatMoney(e.amountMinor, e.currency)}</TableCell>
                    <TableCell>
                      <OutcomeBadge outcome={e.before} />
                    </TableCell>
                    <TableCell>
                      <OutcomeBadge outcome={e.after} />
                    </TableCell>
                    <TableCell className="hidden font-mono text-[12px] text-muted-foreground lg:table-cell">
                      {e.reasonCodes.join(", ") || "none"}
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </div>
        </section>
      )}
    </div>
  );
}

function OutcomeMix({ label, counts, total }: { label: string; counts: Record<Outcome, number>; total: number }) {
  return (
    <div className="space-y-1.5">
      <div className="flex flex-wrap items-baseline justify-between gap-x-3 text-[13px]">
        <span className="font-medium">{label}</span>
        <span className="text-muted-foreground tabular">
          {OUTCOMES.map((o) => `${formatCount(counts[o] ?? 0)} ${PLURAL[o]}`).join(", ")}
        </span>
      </div>
      <div className="flex h-2.5 overflow-hidden rounded-full bg-muted" aria-hidden="true">
        {OUTCOMES.map((o) =>
          (counts[o] ?? 0) > 0 ? <span key={o} className={OUTCOME[o].bg} style={{ width: `${((counts[o] ?? 0) / total) * 100}%` }} /> : null,
        )}
      </div>
    </div>
  );
}

function CheckLine({ check }: { check: ReplayReport["check"] }) {
  const exact = check.matched === check.checked;
  return (
    <div className={cn("flex items-start gap-3 rounded-md border bg-card px-4 py-3", !exact && "border-stepup/50")}>
      {exact ? (
        <CircleCheck className="mt-0.5 size-4 shrink-0 text-approve" aria-hidden="true" />
      ) : (
        <TriangleAlert className="mt-0.5 size-4 shrink-0 text-stepup" aria-hidden="true" />
      )}
      <p className="text-[13px]">
        <span className="font-medium">Replay check: </span>
        {exact
          ? `running each decision again with the rules that made it reproduced all ${formatCount(check.checked)} recorded outcomes, so these numbers can be trusted.`
          : `only ${formatCount(check.matched)} of ${formatCount(check.checked)} decisions came out as recorded. Something changed how rules run; treat these numbers with care.`}
        {check.unknownVersion > 0 &&
          ` ${formatCount(check.unknownVersion)} decisions were made by rule versions this server cannot run, so they were not checked.`}
      </p>
    </div>
  );
}
