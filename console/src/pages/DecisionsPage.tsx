import { useEffect, useState, type FormEvent, type ReactNode } from "react";
import { useSearchParams } from "react-router";
import { useInfiniteQuery } from "@tanstack/react-query";
import { LoaderCircle, Search, X } from "lucide-react";
import { api } from "@/lib/api";
import type { DecisionPage, Outcome } from "@/lib/types";
import { OUTCOME, OUTCOMES } from "@/lib/outcomes";
import { PageHeader } from "@/components/PageHeader";
import { DecisionTable } from "@/components/DecisionTable";
import { EmptyState, ErrorState } from "@/components/States";
import { useNow } from "@/components/Time";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Skeleton } from "@/components/ui/skeleton";
import { cn } from "@/lib/utils";

/**
 * Search every recorded decision. The filters live in the page address
 * (/decisions?q=card-42&outcome=DECLINE), so a filtered view can be bookmarked, shared,
 * and reached with the browser's back button.
 */
export function DecisionsPage() {
  const [params, setParams] = useSearchParams();
  const q = params.get("q") ?? "";
  const outcome = (params.get("outcome") as Outcome | null) ?? null;
  const degraded = params.get("degraded") === "true";
  const shadow = params.get("shadow") === "true";
  const filtered = Boolean(q || outcome || degraded || shadow);

  const [draft, setDraft] = useState(q);
  useEffect(() => setDraft(q), [q]); // keep the box in step with the address (back button, links)
  const now = useNow(10_000);

  const decisions = useInfiniteQuery({
    queryKey: ["decisions", { q, outcome, degraded, shadow }],
    initialPageParam: null as string | null,
    queryFn: ({ pageParam }) => {
      const search = new URLSearchParams({ limit: "50" });
      if (q) search.set("q", q);
      if (outcome) search.set("outcome", outcome);
      if (degraded) search.set("degraded", "true");
      if (shadow) search.set("shadowChanges", "true");
      if (pageParam) search.set("cursor", pageParam);
      return api<DecisionPage>(`/api/decisions?${search}`);
    },
    getNextPageParam: (last) => last.nextCursor,
  });

  function update(changes: Record<string, string | null>) {
    const next = new URLSearchParams(params);
    for (const [key, value] of Object.entries(changes)) {
      if (value) next.set(key, value);
      else next.delete(key);
    }
    setParams(next);
  }

  function submit(event: FormEvent) {
    event.preventDefault();
    update({ q: draft.trim() || null });
  }

  const items = decisions.data?.pages.flatMap((page) => page.items) ?? [];

  return (
    <>
      <PageHeader
        title="Decisions"
        description="Every recorded decision. Search by transaction, card, device or merchant id."
      />

      <div className="mb-4 flex flex-wrap items-center gap-x-4 gap-y-3">
        <form onSubmit={submit} className="flex w-full max-w-[420px] gap-2" role="search">
          <div className="relative flex-1">
            <Search className="pointer-events-none absolute top-1/2 left-3 size-4 -translate-y-1/2 text-muted-foreground" aria-hidden="true" />
            <Input
              value={draft}
              onChange={(e) => setDraft(e.target.value)}
              placeholder="txn-…, card-…, dev-… or mer-…"
              aria-label="Search by id"
              className="pl-9 font-mono text-[13px]"
            />
          </div>
          <Button type="submit" variant="outline">
            Search
          </Button>
        </form>

        <div role="group" aria-label="Outcome" className="inline-flex rounded-md border bg-card p-0.5">
          <SegmentButton active={!outcome} onClick={() => update({ outcome: null })}>
            All
          </SegmentButton>
          {OUTCOMES.map((o) => (
            <SegmentButton key={o} active={outcome === o} onClick={() => update({ outcome: o })}>
              <span className={cn("size-2 rounded-full", OUTCOME[o].bg)} aria-hidden="true" />
              {OUTCOME[o].label}
            </SegmentButton>
          ))}
        </div>

        <ToggleChip pressed={degraded} onClick={() => update({ degraded: degraded ? null : "true" })}>
          Degraded only
        </ToggleChip>
        <ToggleChip pressed={shadow} onClick={() => update({ shadow: shadow ? null : "true" })}>
          Shadow would change
        </ToggleChip>

        {filtered && (
          <Button
            variant="ghost"
            size="sm"
            onClick={() => {
              setDraft("");
              setParams(new URLSearchParams());
            }}
          >
            <X aria-hidden="true" />
            Clear filters
          </Button>
        )}
      </div>

      {decisions.isPending && (
        <div className="space-y-2">
          {Array.from({ length: 8 }, (_, i) => (
            <Skeleton key={i} className="h-10 w-full" />
          ))}
        </div>
      )}
      {decisions.isError && <ErrorState error={decisions.error} onRetry={() => decisions.refetch()} />}

      {decisions.isSuccess &&
        (items.length === 0 ? (
          <EmptyState
            title={filtered ? "No decisions match these filters" : "No decisions recorded yet"}
            action={
              filtered ? (
                <Button
                  variant="outline"
                  onClick={() => {
                    setDraft("");
                    setParams(new URLSearchParams());
                  }}
                >
                  Clear filters
                </Button>
              ) : undefined
            }
          >
            {filtered
              ? "Search matches whole ids. Copy one from the live feed or a decision page."
              : "Decisions appear here as soon as payments are sent."}
          </EmptyState>
        ) : (
          <>
            <DecisionTable items={items} now={now} />
            <div className="mt-4 flex justify-center">
              {decisions.hasNextPage ? (
                <Button variant="outline" onClick={() => decisions.fetchNextPage()} disabled={decisions.isFetchingNextPage}>
                  {decisions.isFetchingNextPage && <LoaderCircle className="animate-spin" aria-hidden="true" />}
                  Load older decisions
                </Button>
              ) : (
                <p className="text-[13px] text-muted-foreground">
                  {items.length} {items.length === 1 ? "decision" : "decisions"}, that is all of them.
                </p>
              )}
            </div>
          </>
        ))}
    </>
  );
}

function SegmentButton({ active, onClick, children }: { active: boolean; onClick: () => void; children: ReactNode }) {
  return (
    <button
      type="button"
      aria-pressed={active}
      onClick={onClick}
      className={cn(
        "inline-flex h-8 items-center gap-1.5 rounded-[5px] px-3 text-[13px] transition-colors",
        active ? "bg-primary font-medium text-primary-foreground" : "text-muted-foreground hover:text-foreground",
      )}
    >
      {children}
    </button>
  );
}

function ToggleChip({ pressed, onClick, children }: { pressed: boolean; onClick: () => void; children: ReactNode }) {
  return (
    <button
      type="button"
      aria-pressed={pressed}
      onClick={onClick}
      className={cn(
        "inline-flex h-8 items-center gap-2 rounded-md border px-3 text-[13px] transition-colors",
        pressed ? "border-foreground/40 bg-card font-medium text-foreground" : "bg-card text-muted-foreground hover:text-foreground",
      )}
    >
      <span
        className={cn(
          "grid size-3.5 place-items-center rounded-[3px] border",
          pressed ? "border-foreground bg-foreground" : "border-input",
        )}
        aria-hidden="true"
      >
        {pressed && <span className="size-1.5 rounded-[1px] bg-card" />}
      </span>
      {children}
    </button>
  );
}
