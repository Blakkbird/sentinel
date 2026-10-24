import { QueryCache, QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { BrowserRouter, Navigate, Route, Routes } from "react-router";
import { ApiError } from "@/lib/api";
import { meQueryKey } from "@/auth/useAuth";
import { RequireAuth } from "@/auth/RequireAuth";
import { AppShell } from "@/components/AppShell";
import { LoginPage } from "@/pages/LoginPage";
import { LivePage } from "@/pages/LivePage";
import { DecisionsPage } from "@/pages/DecisionsPage";
import { DecisionDetailPage } from "@/pages/DecisionDetailPage";
import { RulesPage } from "@/pages/RulesPage";
import { NotFoundPage } from "@/pages/NotFoundPage";

const queryClient: QueryClient = new QueryClient({
  // If any request comes back 401, the session has ended: forget the user, and
  // RequireAuth sends them to the sign-in page.
  queryCache: new QueryCache({
    onError: (error) => {
      if (error instanceof ApiError && error.status === 401) {
        queryClient.setQueryData(meQueryKey, null);
      }
    },
  }),
  defaultOptions: {
    queries: {
      refetchOnWindowFocus: false,
      // Retry network blips and server errors, never "not found" or "not allowed".
      retry: (failures, error) => !(error instanceof ApiError && error.status >= 400 && error.status < 500) && failures < 2,
    },
  },
});

export function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route
            element={
              <RequireAuth>
                <AppShell />
              </RequireAuth>
            }
          >
            <Route index element={<Navigate to="/live" replace />} />
            <Route path="/live" element={<LivePage />} />
            <Route path="/decisions" element={<DecisionsPage />} />
            <Route path="/decisions/:transactionId" element={<DecisionDetailPage />} />
            <Route path="/rules" element={<RulesPage />} />
            <Route path="*" element={<NotFoundPage />} />
          </Route>
        </Routes>
      </BrowserRouter>
    </QueryClientProvider>
  );
}
