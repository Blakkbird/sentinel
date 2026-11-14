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

/**
 * A part of a whole as a percentage: "92%", "0.4%". Shares under 10% and over 90% keep one
 * decimal, because 0.4% degraded and 0% degraded are different situations. With nothing to
 * divide by (no decisions) there is no share, so the answer is a dash, never NaN.
 */
export function formatShare(part: number, whole: number): string {
  if (whole <= 0) return "–";
  if (part <= 0) return "0%";
  if (part >= whole) return "100%";
  const percent = (part / whole) * 100;
  if (percent < 0.1) return "<0.1%";
  if (percent > 99.9) return ">99.9%";
  const text = percent < 10 || percent > 90 ? percent.toFixed(1) : Math.round(percent).toString();
  return text.replace(/\.0$/, "") + "%"; // "6%", not "6.0%"
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

const hourMinuteFormat = new Intl.DateTimeFormat("en-IN", { hour: "2-digit", minute: "2-digit", hourCycle: "h23" });

const dayHourMinuteFormat = new Intl.DateTimeFormat("en-IN", {
  day: "numeric",
  month: "short",
  hour: "2-digit",
  minute: "2-digit",
  hourCycle: "h23",
});

/** "14:05", for chart axes. */
export function formatHourMinute(time: string | number): string {
  return hourMinuteFormat.format(new Date(time));
}

/** "13 Nov, 14:05", for a chart that spans midnight. */
export function formatDayHourMinute(time: string | number): string {
  return dayHourMinuteFormat.format(new Date(time));
}

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
