import type { RuleSetStatus } from "@/lib/types";
import { STATUS_LABEL } from "@/lib/rules";
import { cn } from "@/lib/utils";

// Ink only. On this console colour always means an outcome (or violet: shadow), so a
// version's status is told apart by line and weight instead.
const STYLE: Record<RuleSetStatus, string> = {
  DRAFT: "border-dashed border-foreground/45 text-foreground",
  PENDING: "border-foreground text-foreground font-semibold",
  PUBLISHED: "border-border text-muted-foreground",
  REJECTED: "border-border text-muted-foreground",
  DISCARDED: "border-dashed border-border text-muted-foreground",
};

export function StatusBadge({ status, live, className }: { status: RuleSetStatus; live?: boolean; className?: string }) {
  if (live) {
    return (
      <span
        className={cn(
          "inline-flex h-6 items-center gap-1.5 rounded-full bg-primary px-2.5 text-[12px] font-medium whitespace-nowrap text-primary-foreground",
          className,
        )}
      >
        <span className="size-1.5 rounded-full bg-approve" aria-hidden="true" />
        Live
      </span>
    );
  }
  return (
    <span
      className={cn(
        "inline-flex h-6 items-center rounded-full border px-2.5 text-[12px] font-medium whitespace-nowrap",
        STYLE[status],
        className,
      )}
    >
      {STATUS_LABEL[status]}
    </span>
  );
}
