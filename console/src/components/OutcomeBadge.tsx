import type { Outcome, RuleMode } from "@/lib/types";
import { MODE_LABEL, OUTCOME } from "@/lib/outcomes";
import { cn } from "@/lib/utils";

/** "● Declined" in the outcome's colour. Colour is never the only signal: the word is always there. */
export function OutcomeBadge({ outcome, className }: { outcome: Outcome; className?: string }) {
  const meta = OUTCOME[outcome];
  return (
    <span className={cn("inline-flex items-center gap-1.5 text-[13px] font-medium whitespace-nowrap", meta.listText, className)}>
      <span className={cn("size-2 shrink-0 rounded-full", meta.bg)} aria-hidden="true" />
      {meta.label}
    </span>
  );
}

const MODE_STYLE: Record<RuleMode, string> = {
  ACTIVE: "border-foreground/30 text-foreground",
  // Dashed, because a shadow rule is not real yet: it is watched, never applied.
  SHADOW: "border-dashed border-shadow/60 text-shadow",
  OFF: "border-border text-muted-foreground",
};

export function ModeBadge({ mode }: { mode: RuleMode }) {
  return (
    <span className={cn("inline-flex h-6 items-center rounded-full border px-2 text-[12px] font-medium", MODE_STYLE[mode])}>
      {MODE_LABEL[mode]}
    </span>
  );
}
