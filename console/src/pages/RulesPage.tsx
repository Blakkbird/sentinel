import { useQuery } from "@tanstack/react-query";
import { api } from "@/lib/api";
import type { RuleSetView } from "@/lib/types";
import { OUTCOME } from "@/lib/outcomes";
import { PageHeader } from "@/components/PageHeader";
import { ModeBadge } from "@/components/OutcomeBadge";
import { ErrorState } from "@/components/States";
import { Skeleton } from "@/components/ui/skeleton";
import { cn } from "@/lib/utils";

/** The rule set in force, read-only. Editing and versioning arrive with the rule builder. */
export function RulesPage() {
  const rules = useQuery({
    queryKey: ["rules", "active"],
    queryFn: () => api<RuleSetView>("/api/rules/active"),
  });

  return (
    <>
      <PageHeader
        title="Rules"
        description={
          rules.data
            ? `Version ${rules.data.version} is live. Active rules decide; shadow rules only record what they would have done.`
            : "The rules every payment is checked against."
        }
      />

      {rules.isPending && <Skeleton className="h-72 w-full" />}
      {rules.isError && <ErrorState error={rules.error} onRetry={() => rules.refetch()} />}

      {rules.data && (
        <ol className="divide-y rounded-md border bg-card">
          {rules.data.rules.map((rule) => (
            <li key={rule.code} className="grid gap-x-6 gap-y-2 px-4 py-4 md:grid-cols-[minmax(0,1fr)_auto]">
              <div className="min-w-0">
                <div className="flex flex-wrap items-center gap-2">
                  <span className="font-mono text-[13px] font-medium">{rule.code}</span>
                  <ModeBadge mode={rule.mode} />
                </div>
                <p className="mt-1 max-w-[80ch]">{rule.description}</p>
                <p className="mt-2 font-mono text-[12.5px] text-muted-foreground">if {rule.condition}</p>
              </div>
              <div className="text-[13px] md:text-right">
                <p className={cn("font-medium", OUTCOME[rule.proposes].text)}>{OUTCOME[rule.proposes].verb}</p>
                <p className="font-mono text-[12px] text-muted-foreground">{rule.reasonCode}</p>
              </div>
            </li>
          ))}
        </ol>
      )}
    </>
  );
}
