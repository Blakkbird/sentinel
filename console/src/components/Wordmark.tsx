import { cn } from "@/lib/utils";

/** The product mark: three bars in the three outcome colours, the same shapes as the live tape. */
export function Wordmark({ className }: { className?: string }) {
  return (
    <span className={cn("inline-flex items-center gap-2 text-foreground", className)}>
      <svg viewBox="0 0 18 18" className="size-[18px]" aria-hidden="true">
        <rect x="1" y="6" width="4" height="11" rx="1" className="fill-approve" />
        <rect x="7" y="2" width="4" height="15" rx="1" className="fill-stepup" />
        <rect x="13" y="9" width="4" height="8" rx="1" className="fill-decline" />
      </svg>
      <span className="font-display text-[19px] leading-none font-semibold tracking-[-0.01em]">Sentinel</span>
    </span>
  );
}
