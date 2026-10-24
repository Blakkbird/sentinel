import type { ReactNode } from "react";
import { Navigate, useLocation } from "react-router";
import { useCurrentUser } from "./useAuth";
import { ErrorState } from "@/components/States";
import { Skeleton } from "@/components/ui/skeleton";

/** Shows its children only to a signed-in user; everyone else is sent to the sign-in page. */
export function RequireAuth({ children }: { children: ReactNode }) {
  const me = useCurrentUser();
  const location = useLocation();

  if (me.isPending) {
    return (
      <div className="mx-auto max-w-5xl space-y-3 p-8">
        <Skeleton className="h-8 w-48" />
        <Skeleton className="h-4 w-80" />
        <Skeleton className="h-64 w-full" />
      </div>
    );
  }
  if (me.isError) {
    return (
      <div className="mx-auto max-w-xl p-8">
        <ErrorState error={me.error} onRetry={() => me.refetch()} />
      </div>
    );
  }
  if (!me.data) {
    return <Navigate to="/login" replace state={{ from: location.pathname + location.search }} />;
  }
  return children;
}
