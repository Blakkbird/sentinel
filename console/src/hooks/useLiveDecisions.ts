import { useCallback, useEffect, useRef, useState } from "react";
import { api } from "@/lib/api";
import type { DecisionPage, DecisionSummary } from "@/lib/types";

export type FeedStatus = "connecting" | "live" | "reconnecting" | "closed";

/**
 * The live feed.
 *
 * 1. Load the most recent decisions once, so the page is not empty.
 * 2. Open a Server-Sent Events stream: one long HTTP response that the server keeps
 *    writing to. Each new decision arrives as an event named "decision".
 *    The browser's EventSource reconnects by itself if the connection drops.
 *
 * While paused, new decisions are held back and shown when the feed resumes.
 */
export function useLiveDecisions(max = 200) {
  const [items, setItems] = useState<DecisionSummary[]>([]);
  const [status, setStatus] = useState<FeedStatus>("connecting");
  const [paused, setPaused] = useState(false);
  const [held, setHeld] = useState(0);

  const pausedRef = useRef(false);
  const heldRef = useRef<DecisionSummary[]>([]);
  const arrivedLive = useRef(new Set<string>());

  const add = useCallback(
    (incoming: DecisionSummary[], live: boolean) => {
      setItems((current) => {
        const seen = new Set(current.map((d) => d.decisionId));
        const fresh = incoming.filter((d) => !seen.has(d.decisionId));
        if (live) fresh.forEach((d) => arrivedLive.current.add(d.decisionId));
        const merged = live ? [...fresh, ...current] : [...current, ...fresh];
        return merged.slice(0, max);
      });
    },
    [max],
  );

  useEffect(() => {
    let cancelled = false;
    api<DecisionPage>(`/api/decisions?limit=${Math.min(max, 100)}`)
      .then((page) => {
        if (!cancelled) add(page.items, false);
      })
      .catch(() => {
        // The stream still works; the page simply starts empty.
      });
    return () => {
      cancelled = true;
    };
  }, [add, max]);

  useEffect(() => {
    const source = new EventSource("/api/stream/decisions");

    source.addEventListener("ready", () => setStatus("live"));
    source.addEventListener("decision", (event) => {
      const decision = JSON.parse((event as MessageEvent<string>).data) as DecisionSummary;
      if (pausedRef.current) {
        heldRef.current.unshift(decision);
        setHeld(heldRef.current.length);
      } else {
        add([decision], true);
      }
    });
    source.onopen = () => setStatus("live");
    source.onerror = () => {
      // CLOSED means the browser gave up (for example, the session expired: 401).
      setStatus(source.readyState === EventSource.CLOSED ? "closed" : "reconnecting");
    };

    return () => source.close();
  }, [add]);

  const togglePause = useCallback(() => {
    const next = !pausedRef.current;
    pausedRef.current = next;
    setPaused(next);
    if (!next && heldRef.current.length > 0) {
      add(heldRef.current, true);
      heldRef.current = [];
      setHeld(0);
    }
  }, [add]);

  return { items, status, paused, held, togglePause, arrivedLive: arrivedLive.current };
}
