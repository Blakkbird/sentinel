import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useNavigate } from "react-router";
import { api, json } from "@/lib/api";
import type { FeatureSpec, ReplayReport, RuleSetAction, RuleSetDetail, RuleSetListing } from "@/lib/types";
import type { RuleShape } from "@/lib/rules";

/**
 * Everything the Rules pages fetch and change. Query keys:
 *   ["rule-sets"]        the list of versions
 *   ["rule-set", 3]      one version
 * After any change, the changed version is replaced with the server's answer and the rest are
 * refetched, so every open view (the list, the live rules) catches up by itself.
 */
export function useRuleSetListing() {
  return useQuery({ queryKey: ["rule-sets"], queryFn: () => api<RuleSetListing>("/api/rule-sets") });
}

export function useRuleSet(version: number | undefined, enabled = true) {
  return useQuery({
    queryKey: ["rule-set", version],
    queryFn: () => api<RuleSetDetail>(`/api/rule-sets/${version}`),
    enabled: enabled && version !== undefined,
    retry: false,
  });
}

export function useFeatures() {
  return useQuery({ queryKey: ["features"], queryFn: () => api<FeatureSpec[]>("/api/features"), staleTime: Infinity });
}

function useAfterChange() {
  const queryClient = useQueryClient();
  return (detail: RuleSetDetail) => {
    queryClient.setQueryData(["rule-set", detail.version], detail);
    void queryClient.invalidateQueries({ queryKey: ["rule-sets"] });
    void queryClient.invalidateQueries({ queryKey: ["rule-set"], predicate: (q) => q.queryKey[1] !== detail.version });
    void queryClient.invalidateQueries({ queryKey: ["replay"] });
  };
}

/** Starts a draft (a copy of "from", or of the live version) and opens it. */
export function useCreateDraft() {
  const navigate = useNavigate();
  const after = useAfterChange();
  return useMutation({
    mutationFn: (body: { from?: number; title?: string }) => api<RuleSetDetail>("/api/rule-sets", json("POST", body)),
    onSuccess: (detail) => {
      after(detail);
      navigate(`/rules/${detail.version}`);
    },
  });
}

export function useSaveDraft(version: number) {
  const after = useAfterChange();
  return useMutation({
    mutationFn: (body: { revision: number; title: string; rules: RuleShape[] }) =>
      api<RuleSetDetail>(`/api/rule-sets/${version}`, json("PUT", body)),
    onSuccess: after,
  });
}

/** POST /api/rule-sets/{version}/{step}: discard now; submit, approve and the rest later. */
export function useRuleSetStep(version: number) {
  const after = useAfterChange();
  return useMutation({
    mutationFn: ({ step, body }: { step: string; body?: unknown }) =>
      api<RuleSetDetail>(`/api/rule-sets/${version}/${step}`, json("POST", body ?? {})),
    onSuccess: after,
  });
}

export function permission(detail: RuleSetDetail, action: RuleSetAction) {
  return detail.permissions.find((p) => p.action === action) ?? { action, allowed: false, reason: null };
}

/** Rule Lab: this version against the live rules, on the last N decisions. Runs when the tab opens. */
export function useReplay(version: number, decisions: number) {
  return useQuery({
    queryKey: ["replay", version, decisions],
    queryFn: () => api<ReplayReport>(`/api/rule-sets/${version}/replay?decisions=${decisions}`),
    retry: false,
  });
}
