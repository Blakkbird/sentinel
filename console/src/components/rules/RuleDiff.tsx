import { Fragment } from "react";
import { describeCondition, diffRules, type ChangedField, type RuleChange, type RuleShape } from "@/lib/rules";
import { MODE_LABEL, OUTCOME } from "@/lib/outcomes";
import { EmptyState } from "@/components/States";
import { cn } from "@/lib/utils";

const FIELD_LABEL: Record<ChangedField, string> = {
  mode: "Mode",
  proposes: "Asks for",
  condition: "When",
  reasonCode: "Reason code",
  description: "Description",
};

const KIND_LABEL: Record<RuleChange["kind"], string> = {
  added: "New rule",
  removed: "Removed",
  changed: "Changed",
};

/** What a version changes, rule by rule, compared with the version it is based on. */
export function RuleDiff({ before, after, beforeVersion }: { before: RuleShape[]; after: RuleShape[]; beforeVersion: number }) {
  const { changes, unchanged } = diffRules(before, after);

  if (changes.length === 0) {
    return (
      <EmptyState title={`Same rules as version ${beforeVersion}`}>
        Nothing differs yet. Change a rule on the Rules tab and the difference shows here.
      </EmptyState>
    );
  }

  const count = (kind: RuleChange["kind"]) => changes.filter((c) => c.kind === kind).length;
  const parts = [
    count("changed") > 0 && `${count("changed")} changed`,
    count("added") > 0 && `${count("added")} added`,
    count("removed") > 0 && `${count("removed")} removed`,
    unchanged > 0 && `${unchanged} unchanged`,
  ].filter(Boolean);

  return (
    <div className="space-y-3">
      <p className="text-muted-foreground">
        Compared with version {beforeVersion}: {parts.join(", ")}.
      </p>
      <ol className="divide-y rounded-md border bg-card">
        {changes.map((change) => (
          <li key={`${change.kind}-${change.code}`} className="px-4 py-3">
            <div className="flex flex-wrap items-center gap-2">
              <span className={cn("font-mono text-[13px] font-medium", change.kind === "removed" && "text-muted-foreground line-through")}>
                {change.code}
              </span>
              <span className="text-[12px] text-muted-foreground">{KIND_LABEL[change.kind]}</span>
            </div>
            {change.kind === "changed" ? (
              <dl className="mt-2 grid gap-x-4 gap-y-1.5 text-[13px] sm:grid-cols-[96px_minmax(0,1fr)]">
                {change.fields.map((f) => (
                  <Fragment key={f.field}>
                    <dt className="text-muted-foreground">{FIELD_LABEL[f.field]}</dt>
                    <dd className="min-w-0">
                      <del className="text-muted-foreground decoration-muted-foreground/60">
                        <span className="sr-only">was </span>
                        {f.before}
                      </del>
                      <span className="px-1.5 text-muted-foreground" aria-hidden="true">
                        →
                      </span>
                      <ins className="font-medium no-underline">
                        <span className="sr-only">now </span>
                        {f.after}
                      </ins>
                    </dd>
                  </Fragment>
                ))}
              </dl>
            ) : (
              <p className="mt-1 text-[13px]">
                <span className={cn("font-medium", OUTCOME[change.rule.proposes].text)}>{OUTCOME[change.rule.proposes].verb}</span>
                <span className="text-muted-foreground"> ({MODE_LABEL[change.rule.mode].toLowerCase()}) when </span>
                {describeCondition(change.rule.condition)}
              </p>
            )}
          </li>
        ))}
      </ol>
    </div>
  );
}
