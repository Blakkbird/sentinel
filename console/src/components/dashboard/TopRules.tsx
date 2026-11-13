import { Link } from "react-router";
import type { TopRule } from "@/lib/types";
import { formatCount } from "@/lib/format";
import { ModeBadge } from "@/components/OutcomeBadge";
import { cn } from "@/lib/utils";

/**
 * The rules that fired most, as horizontal bars on one shared scale: the longest bar is the
 * rule that fired most, and every other bar is in proportion to it.
 *
 * Bars are ink. A shadow rule's bar is violet and carries the Shadow badge, because its
 * firing changed nothing: it is a count of what would have happened.
 */
export function TopRules({ rules }: { rules: TopRule[] }) {
  const most = Math.max(1, ...rules.map((rule) => rule.count));
  return (
    <ol className="divide-y rounded-md border bg-card">
      {rules.map((rule) => (
        <li
          key={`${rule.ruleCode}:${rule.mode}`}
          className="grid grid-cols-[minmax(0,1fr)_auto] items-center gap-x-4 gap-y-1.5 px-4 py-2.5 sm:grid-cols-[17rem_minmax(0,1fr)_4.5rem]"
        >
          <div className="flex min-w-0 items-center gap-2">
            <Link
              to="/rules"
              title="See this rule on the Rules page"
              className="truncate font-mono text-[13px] font-medium underline decoration-border underline-offset-4 hover:decoration-foreground"
            >
              {rule.ruleCode}
            </Link>
            {rule.mode === "SHADOW" && <ModeBadge mode="SHADOW" />}
          </div>
          <div className="order-last col-span-2 sm:order-none sm:col-span-1" aria-hidden="true">
            <div
              className={cn("h-3 min-w-[2px] rounded-r-[2px]", rule.mode === "SHADOW" ? "bg-shadow" : "bg-foreground")}
              style={{ width: `${(rule.count / most) * 100}%` }}
            />
          </div>
          <p className="tabular text-right font-medium">
            {formatCount(rule.count)}
            <span className="sr-only"> {rule.count === 1 ? "time" : "times"}</span>
          </p>
        </li>
      ))}
    </ol>
  );
}
