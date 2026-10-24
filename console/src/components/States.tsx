import type { ReactNode } from "react";
import { TriangleAlert } from "lucide-react";
import { ApiError } from "@/lib/api";
import { Button } from "@/components/ui/button";

export function EmptyState({ title, children, action }: { title: string; children?: ReactNode; action?: ReactNode }) {
  return (
    <div className="rounded-md border border-dashed bg-card px-6 py-12 text-center">
      <p className="font-medium">{title}</p>
      {children && <div className="mx-auto mt-1 max-w-[60ch] text-muted-foreground">{children}</div>}
      {action && <div className="mt-4">{action}</div>}
    </div>
  );
}

export function ErrorState({ error, onRetry }: { error: unknown; onRetry?: () => void }) {
  const title = error instanceof ApiError ? error.title : "Something went wrong";
  const detail = error instanceof Error ? error.message : "The request failed.";
  return (
    <div role="alert" className="flex items-start gap-3 rounded-md border border-decline/40 bg-card px-4 py-3">
      <TriangleAlert className="mt-0.5 size-4 shrink-0 text-decline" aria-hidden="true" />
      <div className="min-w-0 flex-1">
        <p className="font-medium">{title}</p>
        <p className="text-muted-foreground">{detail}</p>
      </div>
      {onRetry && (
        <Button variant="outline" size="sm" onClick={onRetry}>
          Try again
        </Button>
      )}
    </div>
  );
}
