import type { ReactNode } from "react";
import { Link, useParams } from "react-router";
import { useQuery } from "@tanstack/react-query";
import { ArrowLeft, CircleDashed, CircleMinus, TriangleAlert } from "lucide-react";
import { api, ApiError } from "@/lib/api";
import type { DecisionView, RuleResultView } from "@/lib/types";
import { formatDateTime, formatMicros, formatMoney } from "@/lib/format";
import { featureLabel, formatFeatureValue } from "@/lib/features";
import { MODE_LABEL, OUTCOME, RESULT_LABEL } from "@/lib/outcomes";
import { EvidenceChip } from "@/components/EvidenceChip";
import { EmptyState, ErrorState } from "@/components/States";
import { Ago, useNow } from "@/components/Time";
import { Skeleton } from "@/components/ui/skeleton";
import { cn } from "@/lib/utils";

/** Everything about one decision: what happened, why, and the facts the rules saw. */
export function DecisionDetailPage() {
  const { transactionId = "" } = useParams();
  const now = useNow(10_000);
  const decision = useQuery({
    queryKey: ["decision", transactionId],
    queryFn: () => api<DecisionView>(`/api/decisions/${encodeURIComponent(transactionId)}`),
    retry: false,
  });

  return (
    <>
      <Link
        to="/decisions"
        className="mb-4 inline-flex items-center gap-1.5 text-[13px] text-muted-foreground hover:text-foreground"
      >
        <ArrowLeft className="size-4" aria-hidden="true" />
        Decisions
      </Link>

      {decision.isPending && (
        <div className="space-y-3">
          <Skeleton className="h-12 w-72" />
          <Skeleton className="h-5 w-96" />
          <Skeleton className="h-48 w-full" />
        </div>
      )}

      {decision.isError &&
        (decision.error instanceof ApiError && decision.error.status === 404 ? (
          <EmptyState title="No decision for this transaction">
            Nothing has been recorded for <span className="font-mono">{transactionId}</span>. Check the id, or search
            for it on the Decisions page.
          </EmptyState>
        ) : (
          <ErrorState error={decision.error} onRetry={() => decision.refetch()} />
        ))}

      {decision.data && <DecisionBody d={decision.data} now={now} />}
    </>
  );
}

function DecisionBody({ d, now }: { d: DecisionView; now: number }) {
  const outcome = OUTCOME[d.outcome];
  const firedActive = d.rules.filter((r) => r.mode === "ACTIVE" && r.result === "FIRED");
  const firedShadow = d.rules.filter((r) => r.mode === "SHADOW" && r.result === "FIRED");
  const abstained = d.rules.filter((r) => r.result === "ABSTAINED");
  const shadowDiffers = d.shadowOutcome !== d.outcome;
  const ruleTextFrom = d.rules.find((r) => r.definitionVersion !== null && r.definitionVersion !== d.ruleSetVersion)?.definitionVersion;

  return (
    <article className="space-y-8">
      {/* What happened */}
      <header className="flex flex-wrap items-start gap-x-8 gap-y-4">
        <div
          className={cn(
            "rounded-md border-2 px-3 py-1.5 font-display text-[22px] leading-none font-semibold tracking-[-0.01em]",
            outcome.text,
            outcome.border,
          )}
        >
          {outcome.label}
        </div>
        <div className="min-w-0">
          <p className="font-display text-[34px] leading-none font-semibold tracking-[-0.01em] tabular">
            {formatMoney(d.amountMinor, d.currency)}
          </p>
          <p className="mt-2 flex flex-wrap items-center gap-x-3 gap-y-1 text-muted-foreground">
            <span className="font-mono text-[13px] text-foreground">{d.transactionId}</span>
            <span>
              Decided <Ago iso={d.decidedAt} now={now} />
            </span>
          </p>
        </div>
      </header>

      <dl className="grid grid-cols-2 gap-x-6 gap-y-4 rounded-md border bg-card px-4 py-4 sm:grid-cols-3 lg:grid-cols-6">
        <Fact label="Card">
          <IdLink id={d.cardId} />
        </Fact>
        <Fact label="Device">{d.deviceId ? <IdLink id={d.deviceId} /> : <span className="text-muted-foreground">Not sent</span>}</Fact>
        <Fact label="Merchant">{d.merchantId ? <IdLink id={d.merchantId} /> : <span className="text-muted-foreground">Not sent</span>}</Fact>
        <Fact label="Rule set">
          <Link
            to={`/rules/${d.ruleSetVersion}`}
            className="underline decoration-border underline-offset-4 hover:decoration-foreground"
            title="The exact rules that made this decision"
          >
            Version {d.ruleSetVersion}
          </Link>
        </Fact>
        <Fact label="Server time">
          <span className="tabular">{formatMicros(d.computeMicros)}</span>
        </Fact>
        <Fact label="Decided at">
          <span className="tabular">{formatDateTime(d.decidedAt)}</span>
        </Fact>
      </dl>

      {/* Why */}
      <section aria-labelledby="why" className="space-y-3">
        <h2 id="why" className="text-[16px] font-semibold">
          Why
        </h2>

        {firedActive.length === 0 ? (
          <p>{d.degraded ? "No rule fired with the facts that were available." : "No active rule fired, so the payment was approved."}</p>
        ) : (
          <ul className="space-y-3">
            {firedActive.map((rule) => (
              <li key={rule.code} className={cn("rounded-md border-l-[3px] bg-card py-3 pr-4 pl-4", OUTCOME[rule.proposes].border)}>
                <RuleHeadline rule={rule} />
                <EvidenceList rule={rule} currency={d.currency} />
              </li>
            ))}
          </ul>
        )}

        {shadowDiffers && (
          <div className="rounded-md border border-dashed border-shadow/60 bg-card px-4 py-3">
            <p className="font-medium text-shadow">
              If the shadow rules were live, this payment would have been {OUTCOME[d.shadowOutcome].label.toLowerCase()}.
            </p>
            <p className="mt-0.5 text-muted-foreground">
              Shadow rules are being tested on real traffic. They are recorded, never applied.
            </p>
            {firedShadow.map((rule) => (
              <div key={rule.code} className="mt-3">
                <RuleHeadline rule={rule} />
                <EvidenceList rule={rule} currency={d.currency} />
              </div>
            ))}
          </div>
        )}

        {d.degraded && (
          <div className="flex items-start gap-3 rounded-md border border-stepup/50 bg-card px-4 py-3">
            <TriangleAlert className="mt-0.5 size-4 shrink-0 text-stepup" aria-hidden="true" />
            <p>
              <span className="font-medium">Decided in degraded mode.</span>{" "}
              <span className="text-muted-foreground">
                The velocity counters were unavailable, so {abstained.length}{" "}
                {abstained.length === 1 ? "rule" : "rules"} could not run. A missing count is never treated as zero; the
                fallback rule covers large payments instead.
              </span>
            </p>
          </div>
        )}
      </section>

      {/* Every rule */}
      <section aria-labelledby="every-rule" className="space-y-3">
        <div>
          <h2 id="every-rule" className="text-[16px] font-semibold">
            Every rule in version {d.ruleSetVersion}
          </h2>
          {ruleTextFrom && (
            <p className="text-[13px] text-muted-foreground">
              Rule descriptions are shown from version {ruleTextFrom}, the closest this server knows. Results are as
              recorded.
            </p>
          )}
        </div>
        <ol className="divide-y rounded-md border bg-card">
          {d.rules.map((rule) => (
            <li key={rule.code} className="grid gap-x-6 gap-y-2 px-4 py-3 md:grid-cols-[minmax(0,1fr)_160px]">
              <div className="min-w-0">
                <div className="flex flex-wrap items-center gap-2">
                  <span className="font-mono text-[13px] font-medium">{rule.code}</span>
                  {rule.mode !== "ACTIVE" && <span className="text-[12px] text-shadow">{MODE_LABEL[rule.mode]}</span>}
                </div>
                {rule.description && <p className="mt-0.5 text-[13px] text-muted-foreground">{rule.description}</p>}
                <EvidenceList rule={rule} currency={d.currency} />
              </div>
              <ResultCell rule={rule} />
            </li>
          ))}
        </ol>
      </section>

      {/* The frozen facts */}
      <section aria-labelledby="facts" className="space-y-3">
        <div>
          <h2 id="facts" className="text-[16px] font-semibold">
            What the rules saw
          </h2>
          <p className="text-[13px] text-muted-foreground">
            Frozen at the moment of the decision. The live counters have moved on since; this snapshot never changes,
            which is why the decision can always be explained and replayed.
          </p>
        </div>
        <div className="overflow-x-auto rounded-md border bg-card">
          <table className="w-full text-sm">
            <tbody>
              {d.facts.map((fact) => (
                <tr key={fact.name} className="border-b last:border-b-0">
                  <td className="w-[38%] px-4 py-2.5">
                    <p>{featureLabel(fact.name)}</p>
                    <p className="font-mono text-[12px] text-muted-foreground">{fact.name}</p>
                  </td>
                  <td className="px-4 py-2.5 text-right font-medium whitespace-nowrap tabular">
                    {formatFeatureValue(fact.name, fact.value, d.currency)}
                  </td>
                  <td className="hidden px-4 py-2.5 text-[13px] text-muted-foreground md:table-cell">{fact.description}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>
    </article>
  );
}

function Fact({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="min-w-0">
      <dt className="text-[12.5px] text-muted-foreground">{label}</dt>
      <dd className="mt-0.5 truncate">{children}</dd>
    </div>
  );
}

/** Clicking an id searches for everything else that card, device or merchant did. */
function IdLink({ id }: { id: string }) {
  return (
    <Link
      to={`/decisions?q=${encodeURIComponent(id)}`}
      className="font-mono text-[13px] underline decoration-border underline-offset-4 hover:decoration-foreground"
      title={`Every decision involving ${id}`}
    >
      {id}
    </Link>
  );
}

function RuleHeadline({ rule }: { rule: RuleResultView }) {
  return (
    <p>
      <span className={cn("font-medium", OUTCOME[rule.proposes].text)}>{OUTCOME[rule.proposes].verb}</span>
      <span className="text-muted-foreground"> by </span>
      <span className="font-mono text-[13px]">{rule.code}</span>
      {rule.description && <span className="block text-muted-foreground">{rule.description}</span>}
    </p>
  );
}

function EvidenceList({ rule, currency }: { rule: RuleResultView; currency: string }) {
  if (rule.evidence.length === 0) return null;
  return (
    <div className="mt-2 flex flex-wrap gap-1.5">
      {rule.evidence.map((e, i) => (
        <EvidenceChip key={`${e.feature}-${i}`} evidence={e} currency={currency} />
      ))}
    </div>
  );
}

function ResultCell({ rule }: { rule: RuleResultView }) {
  const fired = rule.result === "FIRED";
  return (
    <div className="flex items-start gap-2 text-[13px] md:justify-end md:text-right">
      {fired ? (
        <span className={cn("mt-1.5 size-2 shrink-0 rounded-full", OUTCOME[rule.proposes].bg)} aria-hidden="true" />
      ) : rule.result === "ABSTAINED" ? (
        <CircleDashed className="mt-0.5 size-4 shrink-0 text-stepup" aria-hidden="true" />
      ) : (
        <CircleMinus className="mt-0.5 size-4 shrink-0 text-muted-foreground" aria-hidden="true" />
      )}
      <div>
        <p className={cn(fired ? "font-medium" : "text-muted-foreground")}>{RESULT_LABEL[rule.result]}</p>
        <p className="text-[12px] text-muted-foreground">
          {fired ? (rule.mode === "ACTIVE" ? OUTCOME[rule.proposes].verb.toLowerCase() : "recorded only") : `would ${OUTCOME[rule.proposes].verb.toLowerCase()}`}
        </p>
      </div>
    </div>
  );
}
