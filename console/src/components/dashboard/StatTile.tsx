import type { ReactNode } from "react";
import { Link } from "react-router";
import { ArrowRight } from "lucide-react";
import { cn } from "@/lib/utils";

/**
 * One headline number: what it is, the number, and a line of context underneath.
 * With "to", the whole tile is a link to the decisions behind the number.
 */
export function StatTile({
  label,
  value,
  detail,
  to,
  className,
}: {
  label: ReactNode;
  value: ReactNode;
  detail?: ReactNode;
  to?: string;
  className?: string;
}) {
  const body = (
    <>
      <p className="flex items-center gap-1.5 text-[13px] text-muted-foreground">
        {label}
        {to && <ArrowRight className="ml-auto size-3.5 opacity-0 transition-opacity group-hover:opacity-100 group-focus-visible:opacity-100" aria-hidden="true" />}
      </p>
      <div className="mt-1.5 font-display text-[28px] leading-none font-semibold">{value}</div>
      {detail && <p className="mt-2 text-[13px] text-muted-foreground">{detail}</p>}
    </>
  );
  const frame = cn("block rounded-md border bg-card px-4 py-3", className);
  return to ? (
    <Link to={to} className={cn(frame, "group transition-colors hover:border-foreground/40")}>
      {body}
    </Link>
  ) : (
    <div className={frame}>{body}</div>
  );
}
