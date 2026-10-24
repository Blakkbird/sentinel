import { useEffect, useState } from "react";
import { formatAgo, formatDateTime } from "@/lib/format";

/** The current time, refreshed every few seconds, so "12 s ago" labels keep moving. */
export function useNow(intervalMs = 5000) {
  const [now, setNow] = useState(() => Date.now());
  useEffect(() => {
    const id = window.setInterval(() => setNow(Date.now()), intervalMs);
    return () => window.clearInterval(id);
  }, [intervalMs]);
  return now;
}

/** "42 s ago", with the full date and time on hover. */
export function Ago({ iso, now }: { iso: string; now: number }) {
  return (
    <time dateTime={iso} title={formatDateTime(iso)}>
      {formatAgo(iso, now)}
    </time>
  );
}
