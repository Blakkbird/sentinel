// Formatting helpers. Money arrives as whole paise (100 paise = 1 rupee), never as a
// decimal, so the arithmetic is exact. Indian digit grouping comes from the en-IN locale.

const moneyCache = new Map<string, Intl.NumberFormat>();

function moneyFormat(currency: string, withPaise: boolean) {
  const key = currency + (withPaise ? ":p" : "");
  let format = moneyCache.get(key);
  if (!format) {
    try {
      format = new Intl.NumberFormat("en-IN", {
        style: "currency",
        currency,
        minimumFractionDigits: withPaise ? 2 : 0,
        maximumFractionDigits: withPaise ? 2 : 0,
      });
    } catch {
      // Unknown currency code: show a plain number rather than crash the page.
      format = new Intl.NumberFormat("en-IN", { minimumFractionDigits: withPaise ? 2 : 0 });
    }
    moneyCache.set(key, format);
  }
  return format;
}

/** 1234500 paise -> "₹12,345"; 1234567 paise -> "₹12,345.67". */
export function formatMoney(amountMinor: number, currency = "INR"): string {
  const withPaise = amountMinor % 100 !== 0;
  return moneyFormat(currency, withPaise).format(amountMinor / 100);
}

const countFormat = new Intl.NumberFormat("en-IN");

export function formatCount(value: number): string {
  return countFormat.format(value);
}

/** Server compute time: 840 -> "0.84 ms", 12500 -> "12.5 ms". */
export function formatMicros(micros: number): string {
  const ms = micros / 1000;
  return (ms < 10 ? ms.toFixed(2) : ms.toFixed(1)) + " ms";
}

const timeFormat = new Intl.DateTimeFormat("en-IN", {
  hour: "2-digit",
  minute: "2-digit",
  second: "2-digit",
  hour12: false,
});

const dateTimeFormat = new Intl.DateTimeFormat("en-IN", {
  day: "numeric",
  month: "short",
  year: "numeric",
  hour: "2-digit",
  minute: "2-digit",
  second: "2-digit",
  hour12: false,
});

export function formatClock(iso: string): string {
  return timeFormat.format(new Date(iso));
}

export function formatDateTime(iso: string): string {
  return dateTimeFormat.format(new Date(iso));
}

/** "just now", "42 s ago", "5 min ago", "3 h ago", then a date. */
export function formatAgo(iso: string, now = Date.now()): string {
  const seconds = Math.max(0, Math.round((now - new Date(iso).getTime()) / 1000));
  if (seconds < 5) return "just now";
  if (seconds < 60) return `${seconds} s ago`;
  const minutes = Math.floor(seconds / 60);
  if (minutes < 60) return `${minutes} min ago`;
  const hours = Math.floor(minutes / 60);
  if (hours < 24) return `${hours} h ago`;
  return formatDateTime(iso);
}
