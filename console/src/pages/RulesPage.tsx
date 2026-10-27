import { Link } from "react-router";
import { useQuery } from "@tanstack/react-query";
import { api } from "@/lib/api";
import type { RuleSetDetail, RuleSetListing } from "@/lib/types";
import { formatDateTime } from "@/lib/format";
import { PageHeader } from "@/components/PageHeader";
import { RuleCards } from "@/components/rules/RuleCards";
import { VersionList } from "@/components/rules/VersionList";
import { ErrorState } from "@/components/States";
import { useNow } from "@/components/Time";
import { Skeleton } from "@/components/ui/skeleton";

/** The live rules, and every version there has been. */
export function RulesPage() {
  const now = useNow(30_000);
  const listing = useQuery({
    queryKey: ["rule-sets"],
    queryFn: () => api<RuleSetListing>("/api/rule-sets"),
  });
  const liveVersion = listing.data?.liveVersion;
  const live = useQuery({
    queryKey: ["rule-set", liveVersion],
    queryFn: () => api<RuleSetDetail>(`/api/rule-sets/${liveVersion}`),
    enabled: liveVersion !== undefined,
  });

  return (
    <>
      <PageHeader
        title="Rules"
        description={
          liveVersion !== undefined
            ? `Version ${liveVersion} is live. Active rules decide; shadow rules only record what they would have done.`
            : "The rules every payment is checked against."
        }
      />

      {listing.isPending && <Skeleton className="h-72 w-full" />}
      {listing.isError && <ErrorState error={listing.error} onRetry={() => listing.refetch()} />}

      {listing.data && (
        <div className="grid items-start gap-x-6 gap-y-8 xl:grid-cols-[minmax(0,1fr)_340px]">
          <section aria-labelledby="live-rules" className="min-w-0 space-y-3">
            <LiveHeading detail={live.data} version={listing.data.liveVersion} />
            {live.isError && <ErrorState error={live.error} onRetry={() => live.refetch()} />}
            {live.data ? <RuleCards rules={live.data.rules} /> : !live.isError && <Skeleton className="h-72 w-full" />}
          </section>

          <aside aria-labelledby="versions" className="space-y-3">
            <h2 id="versions" className="text-[16px] font-semibold">
              Versions
            </h2>
            <VersionList items={listing.data.items} now={now} />
          </aside>
        </div>
      )}
    </>
  );
}

function LiveHeading({ detail, version }: { detail?: RuleSetDetail; version: number }) {
  const since = detail?.activations.at(-1);
  return (
    <div>
      <h2 id="live-rules" className="text-[16px] font-semibold">
        Live now:{" "}
        <Link to={`/rules/${version}`} className="underline decoration-border underline-offset-4 hover:decoration-foreground">
          version {version}
        </Link>
      </h2>
      {detail && (
        <p className="text-[13px] text-muted-foreground">
          {detail.title}.{since && ` Live since ${formatDateTime(since.activatedAt)}, by ${since.activatedByName}.`}
        </p>
      )}
    </div>
  );
}
