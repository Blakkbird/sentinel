import type { RuleDocument } from "@/lib/types";
import { OUTCOME } from "@/lib/outcomes";
import { describeCondition } from "@/lib/rules";
import { ModeBadge } from "@/components/OutcomeBadge";
import { cn } from "@/lib/utils";

/** A rule set, read-only: what each rule checks and what it asks for when it fires. */
export function RuleCards({ rules }: { rules: RuleDocument[] }) {
  return (
    <ol className="divide-y rounded-md border bg-card">
      {rules.map((rule) => (
        <li key={rule.code} className="grid gap-x-6 gap-y-2 px-4 py-4 md:grid-cols-[minmax(0,1fr)_auto]">
          <div className="min-w-0">
            <div className="flex flex-wrap items-center gap-2">
              <span className="font-mono text-[13px] font-medium">{rule.code}</span>
              <ModeBadge mode={rule.mode} />
            </div>
            <p className="mt-1 max-w-[80ch]">{rule.description}</p>
            <p className="mt-2 text-[13px]">
              <span className="text-muted-foreground">When </span>
              {describeCondition(rule.condition)}
            </p>
            <p className="mt-0.5 font-mono text-[12px] text-muted-foreground">{rule.conditionText}</p>
          </div>
          <div className="text-[13px] md:text-right">
            <p className={cn("font-medium", OUTCOME[rule.proposes].text)}>{OUTCOME[rule.proposes].verb}</p>
            <p className="font-mono text-[12px] text-muted-foreground">{rule.reasonCode}</p>
          </div>
        </li>
      ))}
    </ol>
  );
}
