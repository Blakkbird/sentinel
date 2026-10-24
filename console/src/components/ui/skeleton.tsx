import * as React from "react";
import { cn } from "@/lib/utils";

/** A grey placeholder shown while data loads, shaped like what will replace it. */
export function Skeleton({ className, ...props }: React.HTMLAttributes<HTMLDivElement>) {
  return <div className={cn("animate-pulse rounded-md bg-muted", className)} {...props} />;
}
