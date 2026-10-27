import { useEffect, useState, type ReactNode } from "react";
import { Link, useParams, useSearchParams } from "react-router";
import { ArrowLeft, Copy, LoaderCircle, Save, Trash2, Undo2 } from "lucide-react";
import { ApiError } from "@/lib/api";
import type { RuleSetDetail } from "@/lib/types";
import { formatDateTime } from "@/lib/format";
import { ACTIVATION_LABEL, checkRules, fromEditable, toEditable, type EditableRule, type RuleShape } from "@/lib/rules";
import { permission, useCreateDraft, useFeatures, useRuleSet, useRuleSetStep, useSaveDraft } from "@/hooks/useRuleSets";
import { RuleBuilder } from "@/components/rules/RuleBuilder";
import { RuleCards } from "@/components/rules/RuleCards";
import { RuleDiff } from "@/components/rules/RuleDiff";
import { RuleLabPanel } from "@/components/rules/RuleLabPanel";
import { StatusBadge } from "@/components/rules/StatusBadge";
import { ConfirmDialog } from "@/components/ConfirmDialog";
import { Tabs } from "@/components/Tabs";
import { EmptyState, ErrorState } from "@/components/States";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Skeleton } from "@/components/ui/skeleton";

type Tab = "rules" | "changes" | "lab";
const TABS: Tab[] = ["rules", "changes", "lab"];

/** One version of the rules. A draft opens in the rule builder for its author; anything else is read-only. */
export function RuleSetPage() {
  const { version: param = "" } = useParams();
  const version = Number(param);
  const valid = Number.isInteger(version) && version > 0;
  const detail = useRuleSet(version, valid);

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
        // A new key whenever the server's copy changes (a save, a status change) starts the
        // editor afresh from what was saved.
        <RuleSetBody key={`${detail.data.version}:${detail.data.revision}:${detail.data.status}`} d={detail.data} />
      )}
    </>
  );
}

function RuleSetBody({ d }: { d: RuleSetDetail }) {
  const [params, setParams] = useSearchParams();
  const requested = params.get("tab") as Tab | null;
  const tab: Tab = requested && TABS.includes(requested) ? requested : "rules";
  const editable = permission(d, "EDIT").allowed;

  const [title, setTitle] = useState(d.title);
  const [rules, setRules] = useState<EditableRule[]>(() => d.rules.map(toEditable));
  const [dirty, setDirty] = useState(false);
  const [confirming, setConfirming] = useState<"discard" | null>(null);

  const features = useFeatures();
  const save = useSaveDraft(d.version);
  const step = useRuleSetStep(d.version);
  const copy = useCreateDraft();

  // Closing the tab with unsaved edits asks first.
  useEffect(() => {
    if (!dirty) return;
    const warn = (event: BeforeUnloadEvent) => event.preventDefault();
    window.addEventListener("beforeunload", warn);
    return () => window.removeEventListener("beforeunload", warn);
  }, [dirty]);

  const problems = editable ? checkRules(rules) : new Map<string, string[]>();
  const problemCount = [...problems.values()].reduce((n, list) => n + list.length, 0);
  const serverProblems = save.error instanceof ApiError ? save.error.problems : [];
  const current: RuleShape[] = editable ? rules.map(fromEditable) : d.rules;

  const edit = (next: EditableRule[]) => {
    setRules(next);
    setDirty(true);
  };
  const saveNow = () => save.mutate({ revision: d.revision, title, rules: rules.map(fromEditable) });
  const revert = () => {
    setRules(d.rules.map(toEditable));
    setTitle(d.title);
    setDirty(false);
    save.reset();
  };
  const setTab = (next: Tab) => {
    const p = new URLSearchParams(params);
    if (next === "rules") p.delete("tab");
    else p.set("tab", next);
    setParams(p, { replace: true });
  };

  return (
    <article className="space-y-6 pb-4">
      <VersionHeader
        d={d}
        title={
          editable ? (
            <div className="max-w-[640px] space-y-1">
              <h1 className="sr-only">{d.title}</h1>
              <Label htmlFor="title">What this version changes</Label>
              <Input
                id="title"
                value={title}
                maxLength={120}
                onChange={(e) => {
                  setTitle(e.target.value);
                  setDirty(true);
                }}
                className="h-10 font-display text-[18px] font-semibold"
              />
            </div>
          ) : undefined
        }
        actions={
          editable ? (
            <>
              <Button onClick={saveNow} disabled={!dirty || problemCount > 0 || save.isPending}>
                {save.isPending ? <LoaderCircle className="animate-spin" aria-hidden="true" /> : <Save aria-hidden="true" />}
                Save changes
              </Button>
              {permission(d, "DISCARD").allowed && (
                <Button variant="outline" onClick={() => setConfirming("discard")}>
                  <Trash2 aria-hidden="true" />
                  Discard draft
                </Button>
              )}
            </>
          ) : (
            <Button variant="outline" onClick={() => copy.mutate({ from: d.version })} disabled={copy.isPending}>
              <Copy aria-hidden="true" />
              Copy into a new draft
            </Button>
          )
        }
      />

      {save.isError && serverProblems.length === 0 && <ErrorState error={save.error} />}
      {serverProblems.length > 0 && (
        <div role="alert" className="rounded-md border border-decline/40 bg-card px-4 py-3">
          <p className="font-medium">Not saved: the server found {serverProblems.length === 1 ? "a problem" : `${serverProblems.length} problems`}</p>
          <ul className="mt-1 list-disc space-y-0.5 pl-5 text-[13px] text-muted-foreground">
            {serverProblems.map((p) => (
              <li key={p}>{p}</li>
            ))}
          </ul>
        </div>
      )}
      {copy.isError && <ErrorState error={copy.error} />}
      {step.isError && <ErrorState error={step.error} />}

      <Tabs
        value={tab}
        onChange={setTab}
        items={[
          { value: "rules", label: "Rules" },
          { value: "changes", label: "Changes" },
          { value: "lab", label: "Rule Lab" },
        ]}
      />

      {tab === "rules" &&
        (editable ? (
          features.data ? (
            <RuleBuilder rules={rules} onChange={edit} features={features.data} problems={problems} serverProblems={serverProblems} />
          ) : features.isError ? (
            <ErrorState error={features.error} onRetry={() => features.refetch()} />
          ) : (
            <Skeleton className="h-72 w-full" />
          )
        ) : (
          <RuleCards rules={d.rules} />
        ))}
      {tab === "changes" && <ChangesTab d={d} current={current} />}
      {tab === "lab" && <RuleLabPanel d={d} unsavedEdits={editable && dirty} />}
      {tab === "rules" && !editable && <LivePeriods d={d} />}

      {editable && dirty && (
        <div className="sticky bottom-4 z-10 flex flex-wrap items-center gap-3 rounded-md border bg-card px-4 py-3 shadow-md">
          <p className="mr-auto">
            {problemCount > 0
              ? `Fix ${problemCount} ${problemCount === 1 ? "problem" : "problems"} before saving.`
              : "You have unsaved changes."}
          </p>
          <Button variant="ghost" onClick={revert}>
            <Undo2 aria-hidden="true" />
            Undo all
          </Button>
          <Button onClick={saveNow} disabled={problemCount > 0 || save.isPending}>
            Save changes
          </Button>
        </div>
      )}

      <ConfirmDialog
        open={confirming === "discard"}
        title={`Discard draft v${d.version}?`}
        confirmLabel="Discard draft"
        pending={step.isPending}
        onCancel={() => setConfirming(null)}
        onConfirm={() => step.mutate({ step: "discard" }, { onSettled: () => setConfirming(null) })}
      >
        <p>It stays in the version list, marked as discarded, but nobody can edit or publish it again.</p>
      </ConfirmDialog>
    </article>
  );
}

function VersionHeader({ d, title, actions }: { d: RuleSetDetail; title?: ReactNode; actions?: ReactNode }) {
  return (
    <header className="space-y-3">
      <div className="flex flex-wrap items-center gap-3">
        <span className="font-display text-[34px] leading-none font-semibold tracking-[-0.01em] tabular">v{d.version}</span>
        <StatusBadge status={d.status} live={d.live} />
        {d.live && <StatusBadge status={d.status} />}
        {actions && <div className="flex flex-wrap gap-2 sm:ml-auto">{actions}</div>}
      </div>
      {title ?? <h1 className="font-display text-[22px] leading-tight font-semibold">{d.title}</h1>}
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

function ChangesTab({ d, current }: { d: RuleSetDetail; current: RuleShape[] }) {
  const base = useRuleSet(d.basedOn ?? undefined, d.basedOn !== null);
  if (d.basedOn === null) {
    return <EmptyState title="The first version">There is no earlier version to compare it with.</EmptyState>;
  }
  if (base.isError) return <ErrorState error={base.error} onRetry={() => base.refetch()} />;
  if (!base.data) return <Skeleton className="h-48 w-full" />;
  return <RuleDiff before={base.data.rules} after={current} beforeVersion={d.basedOn} />;
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
