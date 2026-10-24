import { Link } from "react-router";
import { Pause, Play, TriangleAlert } from "lucide-react";
import { useLiveDecisions, type FeedStatus } from "@/hooks/useLiveDecisions";
import { PageHeader } from "@/components/PageHeader";
import { DecisionTape } from "@/components/DecisionTape";
import { DecisionTable } from "@/components/DecisionTable";
import { EmptyState } from "@/components/States";
import { useNow } from "@/components/Time";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";

const STATUS: Record<FeedStatus, { label: string; dot: string }> = {
  connecting: { label: "Connecting", dot: "bg-muted-foreground" },
  live: { label: "Live", dot: "bg-approve" },
  reconnecting: { label: "Reconnecting", dot: "bg-stepup" },
  closed: { label: "Disconnected, reload the page", dot: "bg-decline" },
};

export function LivePage() {
  const feed = useLiveDecisions();
  const now = useNow(5000);

  const perMinute = feed.items.filter((d) => now - new Date(d.decidedAt).getTime() < 60_000).length;
  // Degraded mode is a property of the system, not of one payment: if the latest decisions
  // were made without velocity counters, say so at the top of the page.
  const degraded = feed.items.slice(0, 5).some((d) => d.degraded);

  return (
    <>
      <PageHeader
        title="Live"
        description="Decisions as they are made. Select any one to see why."
        actions={
          <>
            <span className="mr-2 inline-flex items-center gap-2 text-[13px] text-muted-foreground" aria-live="polite">
              <span className={cn("size-2 rounded-full", STATUS[feed.status].dot, feed.status === "live" && !feed.paused && "animate-pulse")} />
              {feed.paused ? "Paused" : STATUS[feed.status].label}
              <span className="tabular">{perMinute} in the last minute</span>
            </span>
            <Button variant="outline" size="sm" onClick={feed.togglePause}>
              {feed.paused ? <Play aria-hidden="true" /> : <Pause aria-hidden="true" />}
              {feed.paused ? (feed.held > 0 ? `Resume (${feed.held} new)` : "Resume") : "Pause"}
            </Button>
          </>
        }
      />

      {degraded && (
        <div role="status" className="mb-5 flex items-start gap-3 rounded-md border border-stepup/50 bg-card px-4 py-3">
          <TriangleAlert className="mt-0.5 size-4 shrink-0 text-stepup" aria-hidden="true" />
          <div>
            <p className="font-medium">Velocity counters are unavailable</p>
            <p className="text-muted-foreground">
              Decisions are being made without them (degraded mode), so velocity rules cannot run and the fallback
              rule steps up payments above ₹10,000.{" "}
              <Link to="/decisions?degraded=true" className="text-foreground underline underline-offset-4">
                See degraded decisions
              </Link>
            </p>
          </div>
        </div>
      )}

      <DecisionTape items={feed.items} />

      <div className="mt-6">
        {feed.items.length === 0 ? (
          <EmptyState title="No decisions yet">
            Start the simulator to send some payments: <span className="font-mono">py simulator\traffic.py</span>
          </EmptyState>
        ) : (
          <DecisionTable items={feed.items} now={now} arrivedLive={feed.arrivedLive} />
        )}
      </div>
    </>
  );
}
