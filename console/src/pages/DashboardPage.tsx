import { Link, useSearchParams } from "react-router";
import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { api } from "@/lib/api";
import type { DashboardView, DashboardWindow, Outcome } from "@/lib/types";
import { formatClock, formatCount, formatMicros, formatShare } from "@/lib/format";
import { OUTCOME, OUTCOMES } from "@/lib/outcomes";
import { PageHeader } from "@/components/PageHeader";
import { EmptyState, ErrorState } from "@/components/States";
import { DecisionsOverTime, bucketLabel } from "@/components/dashboard/DecisionsOverTime";
import { StatTile } from "@/components/dashboard/StatTile";
import { TopRules } from "@/components/dashboard/TopRules";
import { Button } from "@/components/ui/button";
import { Segmented } from "@/components/ui/segmented";
import { Skeleton } from "@/components/ui/skeleton";
import { cn } from "@/lib/utils";

const WINDOWS: { value: DashboardWindow; label: string; span: string }[] = [
  { value: "15m", label: "15 min", span: "the last 15 minutes" },
  { value: "1h", label: "1 hour", span: "the last hour" },
  { value: "24h", label: "24 hours", span: "the last 24 hours" },
];
const DEFAULT_WINDOW: DashboardWindow = "1h";

/**
 * The state of decisioning at a glance: how much traffic, how it was decided, how fast, and
 * which rules did the work. The window lives in the page address (/dashboard?window=24h), so a
 * view can be bookmarked and the back button undoes a switch.
 */
export function DashboardPage() {
  const [params, setParams] = useSearchParams();
  const requested = params.get("window");
  const selected = WINDOWS.find((w) => w.value === requested) ?? WINDOWS.find((w) => w.value === DEFAULT_WINDOW)!;

  const dashboard = useQuery({
    queryKey: ["dashboard", selected.value],
    queryFn: () => api<DashboardView>(`/api/dashboard?window=${selected.value}`),
    // Fresh numbers every ten seconds while the page is being looked at. A tab in the
    // background stops asking: nobody is reading it, and each request costs the database three queries.
    refetchInterval: 10_000,
    refetchIntervalInBackground: false,
    // While another window loads, keep showing the one already on screen (dimmed) instead of
    // flashing back to grey placeholders.
    placeholderData: keepPreviousData,
  });

  function choose(next: DashboardWindow) {
    const search = new URLSearchParams(params);
    if (next === DEFAULT_WINDOW) search.delete("window");
    else search.set("window", next);
    setParams(search);
  }

  const data = dashboard.data;

  return (
    <>
      <PageHeader
        title="Dashboard"
        description={`How payments were decided in ${selected.span}.`}
        actions={
          <>
            {data && (
              <span className="mr-2 text-[13px] text-muted-foreground">
                Updated <span className="tabular">{formatClock(data.generatedAt)}</span>
              </span>
            )}
            <Segmented label="Time window" value={selected.value} options={WINDOWS} onChange={choose} />
          </>
        }
      />

      {dashboard.isError && (
        <div className="mb-5">
          <ErrorState error={dashboard.error} onRetry={() => dashboard.refetch()} />
        </div>
      )}

      {dashboard.isPending && <Placeholders />}

      {data && (
        <div className={cn("space-y-8 transition-opacity", dashboard.isPlaceholderData && "opacity-50")}>
          <Tiles data={data} />

          {data.totals.decisions === 0 ? (
            <EmptyState
              title={`No decisions in ${selected.span}`}
              action={
                selected.value !== "24h" ? (
                  <Button variant="outline" onClick={() => choose("24h")}>
                    Look at the last 24 hours
                  </Button>
                ) : undefined
              }
            >
              {selected.value !== "24h" ? (
                "Nothing was decided in this window. A longer one may have something to show."
              ) : (
                <>
                  Start the simulator to send some payments: <span className="font-mono">py simulator\traffic.py</span>
                </>
              )}
            </EmptyState>
          ) : (
            <>
              <section aria-labelledby="over-time" className="space-y-3">
                <div className="flex flex-wrap items-baseline justify-between gap-x-6 gap-y-1">
                  <h2 id="over-time" className="text-[16px] font-semibold">
                    Decisions over time
                  </h2>
                  <p className="text-[13px] text-muted-foreground">
                    One bar per {bucketLabel(data.bucketSeconds)}. The last bar is still filling.
                  </p>
                </div>
                <DecisionsOverTime series={data.series} bucketSeconds={data.bucketSeconds} />
                <Legend totals={data.totals} />
              </section>

              <section aria-labelledby="top-rules" className="space-y-3">
                <div className="flex flex-wrap items-baseline justify-between gap-x-6 gap-y-1">
                  <h2 id="top-rules" className="text-[16px] font-semibold">
                    Rules that fired most
                  </h2>
                  <p className="text-[13px] text-muted-foreground">
                    How many decisions each rule fired on.{" "}
                    <span className="text-shadow">Violet is a shadow rule: recorded, never applied.</span>
                  </p>
                </div>
                {data.topRules.length === 0 ? (
                  <EmptyState title="No rule fired">
                    Every payment in this window passed every rule. The rules themselves are on the{" "}
                    <Link to="/rules" className="text-foreground underline underline-offset-4">
                      Rules page
                    </Link>
                    .
                  </EmptyState>
                ) : (
                  <TopRules rules={data.topRules} />
                )}
              </section>
            </>
          )}
        </div>
      )}
    </>
  );
}

const COUNT: Record<Outcome, (totals: DashboardView["totals"]) => number> = {
  APPROVE: (t) => t.approve,
  STEP_UP: (t) => t.stepUp,
  DECLINE: (t) => t.decline,
};

function Tiles({ data }: { data: DashboardView }) {
  const { totals, computeMicros } = data;
  return (
    <div className="grid grid-flow-dense grid-cols-2 gap-3 xl:grid-cols-[1fr_1.7fr_1fr_1fr_1fr]">
      <StatTile
        label="Decisions"
        value={formatCount(totals.decisions)}
        detail={totals.decisions === 1 ? "payment decided" : "payments decided"}
      />

      <StatTile
        className="col-span-2 xl:col-span-1"
        label="Outcome mix"
        value={
          <div className="flex gap-6">
            {OUTCOMES.map((outcome) => (
              <div key={outcome}>
                {formatShare(COUNT[outcome](totals), totals.decisions)}
                <p className="mt-1.5 flex items-center gap-1.5 font-sans text-[13px] leading-normal font-normal text-muted-foreground">
                  <span className={cn("size-2 rounded-full", OUTCOME[outcome].bg)} aria-hidden="true" />
                  {OUTCOME[outcome].label}
                </p>
              </div>
            ))}
          </div>
        }
        detail={<MixBar totals={totals} />}
      />

      <StatTile
        label="p95 compute time"
        value={computeMicros.p95 === null ? "–" : formatMicros(computeMicros.p95)}
        detail={
          computeMicros.p50 === null
            ? "No decisions to time"
            : `95 in 100 were faster. Median ${formatMicros(computeMicros.p50)}`
        }
      />

      <StatTile
        to="/decisions?degraded=true"
        label="Degraded"
        value={formatShare(totals.degraded, totals.decisions)}
        detail={`${formatCount(totals.degraded)} decided without velocity counters`}
      />

      <StatTile
        to="/decisions?shadow=true"
        label={
          <>
            <span className="size-2.5 rounded-[2px] bg-shadow" aria-hidden="true" />
            Shadow changes
          </>
        }
        value={formatCount(totals.shadowChanges)}
        detail={
          totals.decisions === 0
            ? "decisions a shadow rule would change"
            : `${formatShare(totals.shadowChanges, totals.decisions)} of decisions a shadow rule would change`
        }
      />
    </div>
  );
}

/** The outcome mix as one thin bar cut into three, each part as wide as its share. */
function MixBar({ totals }: { totals: DashboardView["totals"] }) {
  if (totals.decisions === 0) {
    return <span className="block h-2 rounded-full bg-muted" aria-hidden="true" />;
  }
  return (
    <span className="flex h-2 gap-[2px] overflow-hidden rounded-full" aria-hidden="true">
      {OUTCOMES.map((outcome) => {
        const count = COUNT[outcome](totals);
        return count > 0 ? <span key={outcome} className={cn("min-w-[3px]", OUTCOME[outcome].bg)} style={{ flexGrow: count }} /> : null;
      })}
    </span>
  );
}

/** What the colours in the chart mean, with the window's total for each. Same shape as the tape's legend. */
function Legend({ totals }: { totals: DashboardView["totals"] }) {
  return (
    <div className="flex flex-wrap items-center gap-x-5 gap-y-1 text-[13px]">
      {OUTCOMES.map((outcome) => (
        <span key={outcome} className="inline-flex items-center gap-1.5">
          <span className={cn("size-2.5 rounded-[2px]", OUTCOME[outcome].bg)} aria-hidden="true" />
          <span className="tabular font-medium">{formatCount(COUNT[outcome](totals))}</span>
          <span className="text-muted-foreground">{OUTCOME[outcome].label.toLowerCase()}</span>
        </span>
      ))}
    </div>
  );
}

/** Grey shapes where the tiles, the chart and the rules will be, while the first answer is on its way. */
function Placeholders() {
  return (
    <div className="space-y-8">
      <div className="grid grid-flow-dense grid-cols-2 gap-3 xl:grid-cols-[1fr_1.7fr_1fr_1fr_1fr]">
        {Array.from({ length: 5 }, (_, i) => (
          <Skeleton key={i} className={cn("h-[104px]", i === 1 && "col-span-2 xl:col-span-1")} />
        ))}
      </div>
      <Skeleton className="h-[232px] w-full" />
      <Skeleton className="h-64 w-full" />
    </div>
  );
}
