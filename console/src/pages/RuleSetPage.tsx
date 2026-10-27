import { Link, useParams } from "react-router";
import { useQuery } from "@tanstack/react-query";
import { ArrowLeft } from "lucide-react";
import { api, ApiError } from "@/lib/api";
import type { RuleSetDetail } from "@/lib/types";
import { formatDateTime } from "@/lib/format";
import { ACTIVATION_LABEL } from "@/lib/rules";
import { RuleCards } from "@/components/rules/RuleCards";
import { StatusBadge } from "@/components/rules/StatusBadge";
import { EmptyState, ErrorState } from "@/components/States";
import { Skeleton } from "@/components/ui/skeleton";

/** One version of the rules: what it contains, who wrote and reviewed it, and when it was live. */
export function RuleSetPage() {
  const { version: param = "" } = useParams();
  const version = Number(param);
  const valid = Number.isInteger(version) && version > 0;
  const detail = useQuery({
    queryKey: ["rule-set", version],
    queryFn: () => api<RuleSetDetail>(`/api/rule-sets/${version}`),
    enabled: valid,
    retry: false,
  });

  return (
    <>
      <Link to="/rules" className="mb-4 inline-flex items-center gap-1.5 text-[13px] text-muted-foreground hover:text-foreground">
        <ArrowLeft className="size-4" aria-hidden="true" />
        Rules
      </Link>

      {!valid || (detail.error instanceof ApiError && detail.error.status === 404) ? (
        <EmptyState title="No such version">
          There is no rule set version <span className="font-mono">{param}</span>. Pick one from the Rules page.
        </EmptyState>
      ) : detail.isError ? (
        <ErrorState error={detail.error} onRetry={() => detail.refetch()} />
      ) : detail.isPending ? (
        <div className="space-y-3">
          <Skeleton className="h-10 w-80" />
          <Skeleton className="h-5 w-96" />
          <Skeleton className="h-72 w-full" />
        </div>
      ) : (
        <article className="space-y-8">
          <VersionHeader d={detail.data} />
          <RuleCards rules={detail.data.rules} />
          <LivePeriods d={detail.data} />
        </article>
      )}
    </>
  );
}

function VersionHeader({ d }: { d: RuleSetDetail }) {
  return (
    <header className="space-y-2">
      <div className="flex flex-wrap items-center gap-3">
        <span className="font-display text-[34px] leading-none font-semibold tracking-[-0.01em] tabular">v{d.version}</span>
        <StatusBadge status={d.status} live={d.live} />
        {d.live && <StatusBadge status={d.status} />}
      </div>
      <h1 className="font-display text-[22px] leading-tight font-semibold">{d.title}</h1>
      <p className="text-[13px] text-muted-foreground">
        {d.basedOn !== null ? (
          <>
            Changes{" "}
            <Link to={`/rules/${d.basedOn}`} className="underline decoration-border underline-offset-4 hover:decoration-foreground">
              version {d.basedOn}
            </Link>
            .{" "}
          </>
        ) : (
          "The first version. "
        )}
        Written by {d.createdByName}, {formatDateTime(d.createdAt)}.
        {d.reviewedByName && d.reviewedAt && ` Reviewed by ${d.reviewedByName}, ${formatDateTime(d.reviewedAt)}.`}
      </p>
      {d.reviewNote && <p className="max-w-[80ch] text-[13px]">Review note: {d.reviewNote}</p>}
    </header>
  );
}

function LivePeriods({ d }: { d: RuleSetDetail }) {
  return (
    <section aria-labelledby="live-periods" className="space-y-2">
      <h2 id="live-periods" className="text-[16px] font-semibold">
        When it was live
      </h2>
      {d.activations.length === 0 ? (
        <p className="text-muted-foreground">This version has never been live.</p>
      ) : (
        <ul className="space-y-1 text-[13px]">
          {d.activations.map((a) => (
            <li key={a.id}>
              <span className="tabular">{formatDateTime(a.activatedAt)}</span>
              <span className="text-muted-foreground">
                {" "}
                {ACTIVATION_LABEL[a.kind]}, by {a.activatedByName}
              </span>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}
