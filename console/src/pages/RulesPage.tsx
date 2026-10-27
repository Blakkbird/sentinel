import { useState } from "react";
import { Link } from "react-router";
import { FilePlus2, LoaderCircle, RotateCcw } from "lucide-react";
import type { RuleSetDetail, RuleSetListing } from "@/lib/types";
import { useCurrentUser } from "@/auth/useAuth";
import { ConfirmDialog } from "@/components/ConfirmDialog";
import { Ago } from "@/components/Time";
import { formatDateTime } from "@/lib/format";
import { PageHeader } from "@/components/PageHeader";
import { RuleCards } from "@/components/rules/RuleCards";
import { VersionList } from "@/components/rules/VersionList";
import { ErrorState } from "@/components/States";
import { useNow } from "@/components/Time";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { useCreateDraft, useRuleSet, useRuleSetListing, useRuleSetStep } from "@/hooks/useRuleSets";

/** The live rules, and every version there has been. */
export function RulesPage() {
  const now = useNow(30_000);
  const listing = useRuleSetListing();
  const liveVersion = listing.data?.liveVersion;
  const live = useRuleSet(liveVersion);
  const create = useCreateDraft();

  return (
    <>
      <PageHeader
        title="Rules"
        description={
          liveVersion !== undefined
            ? `Version ${liveVersion} is live. Active rules decide; shadow rules only record what they would have done.`
            : "The rules every payment is checked against."
        }
        actions={
          <Button onClick={() => create.mutate({})} disabled={create.isPending}>
            {create.isPending ? <LoaderCircle className="animate-spin" aria-hidden="true" /> : <FilePlus2 aria-hidden="true" />}
            New draft
          </Button>
        }
      />
      {create.isError && (
        <div className="mb-5">
          <ErrorState error={create.error} />
        </div>
      )}

      {listing.isPending && <Skeleton className="h-72 w-full" />}
      {listing.isError && <ErrorState error={listing.error} onRetry={() => listing.refetch()} />}

      {listing.data && <Pending listing={listing.data} now={now} />}

      {listing.data && (
        <div className="grid items-start gap-x-6 gap-y-8 xl:grid-cols-[minmax(0,1fr)_340px]">
          <section aria-labelledby="live-rules" className="min-w-0 space-y-3">
            <LiveHeading detail={live.data} version={listing.data.liveVersion} previous={listing.data.previousVersion} />
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

/** Changes waiting for an admin, at the top, so they are not missed. */
function Pending({ listing, now }: { listing: RuleSetListing; now: number }) {
  const pending = listing.items.filter((item) => item.status === "PENDING");
  if (pending.length === 0) return null;
  return (
    <div className="mb-6 rounded-md border border-foreground/40 bg-card px-4 py-3">
      <p className="font-medium">
        {pending.length === 1 ? "1 change is" : `${pending.length} changes are`} waiting for approval
      </p>
      <ul className="mt-1 space-y-0.5">
        {pending.map((p) => (
          <li key={p.version}>
            <Link to={`/rules/${p.version}?tab=changes`} className="underline decoration-border underline-offset-4 hover:decoration-foreground">
              Version {p.version}: {p.title}
            </Link>
            <span className="text-[13px] text-muted-foreground">
              {" "}
              by {p.createdByName}, sent <Ago iso={p.submittedAt ?? p.updatedAt} now={now} />
            </span>
          </li>
        ))}
      </ul>
    </div>
  );
}

function LiveHeading({ detail, version, previous }: { detail?: RuleSetDetail; version: number; previous: number | null }) {
  const since = detail?.activations.at(-1);
  const isAdmin = useCurrentUser().data?.role === "ADMIN";
  return (
    <div className="flex flex-wrap items-start justify-between gap-3">
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
      {isAdmin && previous !== null && <RollbackButton to={previous} from={version} />}
    </div>
  );
}

/** One click (and one confirmation) to make the previous version live again. Admins only. */
function RollbackButton({ to, from }: { to: number; from: number }) {
  const [open, setOpen] = useState(false);
  const [note, setNote] = useState("");
  const step = useRuleSetStep(to);
  return (
    <>
      <Button variant="outline" onClick={() => setOpen(true)}>
        <RotateCcw aria-hidden="true" />
        Roll back to version {to}
      </Button>
      <ConfirmDialog
        open={open}
        title={`Roll back to version ${to}?`}
        confirmLabel="Roll back"
        pending={step.isPending}
        onCancel={() => setOpen(false)}
        onConfirm={() =>
          step.mutate(
            { step: "activate", body: { note } },
            {
              onSuccess: () => {
                setOpen(false);
                setNote("");
              },
            },
          )
        }
      >
        <p>
          Version {to} replaces version {from} for every payment from the moment you confirm. It needs no second approval,
          and it is recorded in the audit log.
        </p>
        <div className="space-y-1">
          <Label htmlFor="rollback-note">Why (optional, for the record)</Label>
          <Textarea id="rollback-note" rows={2} maxLength={500} value={note} onChange={(e) => setNote(e.target.value)} />
        </div>
        {step.isError && <ErrorState error={step.error} />}
      </ConfirmDialog>
    </>
  );
}
