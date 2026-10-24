import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useNavigate } from "react-router";
import { api, ApiError } from "@/lib/api";
import type { CurrentUser } from "@/lib/types";

export const meQueryKey = ["me"] as const;

/** Who is signed in, or null. A 401 from the backend simply means "nobody". */
async function fetchMe(): Promise<CurrentUser | null> {
  try {
    return await api<CurrentUser>("/api/auth/me");
  } catch (error) {
    if (error instanceof ApiError && error.status === 401) return null;
    throw error;
  }
}

export function useCurrentUser() {
  return useQuery({ queryKey: meQueryKey, queryFn: fetchMe, staleTime: 5 * 60_000, retry: false });
}

/**
 * Signing in posts the username and password as an ordinary form (that is what Spring
 * Security's form login reads). On success the backend sets a session cookie, which the
 * browser then sends with every request, and answers with the user.
 */
export function useSignIn() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ username, password }: { username: string; password: string }) =>
      api<CurrentUser>("/api/auth/login", {
        method: "POST",
        body: new URLSearchParams({ username, password }),
      }),
    onSuccess: (user) => queryClient.setQueryData(meQueryKey, user),
  });
}

export function useSignOut() {
  const queryClient = useQueryClient();
  const navigate = useNavigate();
  return useMutation({
    mutationFn: () => api<void>("/api/auth/logout", { method: "POST" }),
    onSettled: () => {
      queryClient.clear();
      queryClient.setQueryData(meQueryKey, null);
      navigate("/login", { replace: true });
    },
  });
}
