import { Link } from "react-router";
import type { RuleSetSummary } from "@/lib/types";
import { StatusBadge } from "./StatusBadge";
import { Ago } from "@/components/Time";

/** Every version, newest first. The number leads, because it is what decisions refer to. */
export function VersionList({ items, now }: { items: RuleSetSummary[]; now: number }) {
  return (
    <ol className="divide-y rounded-md border bg-card">
      {items.map((v) => (
        <li key={v.version}>
          <Link to={`/rules/${v.version}`} className="flex items-start gap-3 px-4 py-3 transition-colors hover:bg-muted/50">
            <span className="w-9 shrink-0 font-display text-[18px] leading-6 font-semibold tabular">v{v.version}</span>
            <span className="min-w-0 flex-1">
              <span className="block truncate font-medium" title={v.title}>
                {v.title}
              </span>
              <span className="block text-[12.5px] text-muted-foreground">
                {v.createdByName}, <Ago iso={v.updatedAt} now={now} />
              </span>
            </span>
            <StatusBadge status={v.status} live={v.live} />
          </Link>
        </li>
      ))}
    </ol>
  );
}
