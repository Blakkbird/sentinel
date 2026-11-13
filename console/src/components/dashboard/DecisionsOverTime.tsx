import { useEffect, useRef, useState, type PointerEvent } from "react";
import type { DashboardPoint, Outcome } from "@/lib/types";
import { formatCount, formatDayHourMinute, formatHourMinute } from "@/lib/format";
import { OUTCOME } from "@/lib/outcomes";
import { cn } from "@/lib/utils";

/**
 * Decisions over time: one bar per bucket, oldest on the left. Each bar is a stack: approvals
 * at the bottom, step-ups above them, declines on top, so the height is the traffic and the
 * colours are the mix. An attack shows up as red growing on top of the bars.
 *
 * Drawn by hand as SVG, in real pixels: the component measures its own width and places
 * every rectangle itself. That is what allows the small things a stretched drawing cannot do:
 * a fixed gap between the parts of a stack, and a minimum height so that a single decline
 * among a thousand approvals is still a visible mark.
 */

// Bottom to top. The order never changes, so position says which outcome a part is even
// where amber and red are hard to tell apart.
const STACK: { outcome: Outcome; value: (p: DashboardPoint) => number; fill: string }[] = [
  { outcome: "APPROVE", value: (p) => p.approve, fill: "fill-approve" },
  { outcome: "STEP_UP", value: (p) => p.stepUp, fill: "fill-stepup" },
  { outcome: "DECLINE", value: (p) => p.decline, fill: "fill-decline" },
];

const HEIGHT = 232;
const PAD = { top: 14, right: 12, bottom: 24, left: 40 };
const TOOLTIP_WIDTH = 176;

export function DecisionsOverTime({ series, bucketSeconds }: { series: DashboardPoint[]; bucketSeconds: number }) {
  const frame = useRef<HTMLDivElement>(null);
  const width = useWidth(frame);
  const [hover, setHover] = useState<number | null>(null);

  const n = series.length;
  const totals = series.map((p) => p.approve + p.stepUp + p.decline);
  const top = roundUp(Math.max(1, ...totals));
  const ticks = top % 2 === 0 ? [0, top / 2, top] : [0, top];

  const plotWidth = Math.max(0, width - PAD.left - PAD.right);
  const plotHeight = HEIGHT - PAD.top - PAD.bottom;
  const slot = n > 0 ? plotWidth / n : 0;
  const barWidth = Math.max(1, Math.min(24, slot * 0.7));
  // The strip of card colour between the parts of a stack, and the smallest a part that is not
  // zero is ever drawn: enough to leave two pixels of colour above the gap. Thin bars (a day
  // on a phone) get a thinner gap, or the stack would be more gap than bar.
  const gap = barWidth >= 4 ? 2 : 1;
  const minPart = gap + 2;
  const y = (count: number) => PAD.top + plotHeight - (count / top) * plotHeight;
  const spansDays = n * bucketSeconds > 12 * 3600;

  function pointAt(event: PointerEvent<SVGSVGElement>) {
    const x = event.clientX - event.currentTarget.getBoundingClientRect().left - PAD.left;
    setHover(slot > 0 && x >= 0 && x < plotWidth ? Math.min(n - 1, Math.floor(x / slot)) : null);
  }

  const hovered = hover !== null && hover < n ? series[hover] : null;

  return (
    <div ref={frame} className="relative rounded-md border bg-card">
      {width > 0 && (
        <svg
          width={width}
          height={HEIGHT}
          role="img"
          aria-label={`Decisions per ${bucketLabel(bucketSeconds)}, stacked by outcome. The busiest had ${formatCount(Math.max(0, ...totals))}. The table that follows has every figure.`}
          className="block touch-pan-y"
          onPointerMove={pointAt}
          onPointerDown={pointAt}
          onPointerLeave={() => setHover(null)}
        >
          {ticks.map((tick) => (
            <g key={tick}>
              <line x1={PAD.left} x2={width - PAD.right} y1={y(tick)} y2={y(tick)} className="stroke-border" shapeRendering="crispEdges" />
              <text x={PAD.left - 8} y={y(tick)} dy="0.32em" textAnchor="end" className="tabular fill-muted-foreground text-[11px]">
                {formatCount(tick)}
              </text>
            </g>
          ))}

          {timeLabels(series, bucketSeconds).map((i) => {
            const x = PAD.left + i * slot;
            return (
              <g key={series[i].at}>
                <line x1={x} x2={x} y1={y(0)} y2={y(0) + 4} className="stroke-border" shapeRendering="crispEdges" />
                <text
                  x={x}
                  y={HEIGHT - 6}
                  // The first and last labels would hang over the edge if centred on their tick.
                  textAnchor={x - PAD.left < 18 ? "start" : width - PAD.right - x < 18 ? "end" : "middle"}
                  className="tabular fill-muted-foreground text-[11px]"
                >
                  {formatHourMinute(series[i].at)}
                </text>
              </g>
            );
          })}

          {hover !== null && (
            <rect x={PAD.left + hover * slot} y={PAD.top} width={slot} height={plotHeight} className="fill-foreground" opacity={0.07} />
          )}

          {series.map((point, i) => {
            const x = PAD.left + i * slot + (slot - barWidth) / 2;
            let base = y(0);
            return STACK.map(({ outcome, value, fill }) => {
              const count = value(point);
              if (count === 0) return null;
              const partHeight = Math.max(minPart, (count / top) * plotHeight);
              const lowest = base === y(0);
              base -= partHeight;
              // Every part but the lowest gives up the gap at its foot: that strip of card
              // colour is what separates it from the part underneath.
              return (
                <rect
                  key={`${point.at}:${outcome}`}
                  x={x}
                  y={base}
                  width={barWidth}
                  height={partHeight - (lowest ? 0 : gap)}
                  rx={barWidth >= 4 ? 1 : 0}
                  className={fill}
                />
              );
            });
          })}
        </svg>
      )}
      {width === 0 && <div style={{ height: HEIGHT }} />}

      {hovered && hover !== null && (
        <div
          role="presentation"
          className="pointer-events-none absolute top-2 z-10 rounded-md border bg-popover px-3 py-2 text-[12.5px] text-popover-foreground shadow-md"
          // Beside the bar being read, on whichever side has more room, so it never covers it.
          style={{
            width: TOOLTIP_WIDTH,
            left:
              hover < n / 2
                ? Math.min(width - TOOLTIP_WIDTH - 8, PAD.left + (hover + 1) * slot + 8)
                : Math.max(8, PAD.left + hover * slot - TOOLTIP_WIDTH - 8),
          }}
        >
          <p className="tabular text-muted-foreground">
            {(spansDays ? formatDayHourMinute : formatHourMinute)(hovered.at)} to{" "}
            {formatHourMinute(new Date(hovered.at).getTime() + bucketSeconds * 1000)}
          </p>
          <ul className="mt-1.5 space-y-0.5">
            {[...STACK].reverse().map(({ outcome, value }) => (
              <li key={outcome} className="flex items-center gap-2">
                <span className={cn("size-2.5 rounded-[2px]", OUTCOME[outcome].bg)} aria-hidden="true" />
                <span className="text-muted-foreground">{OUTCOME[outcome].label}</span>
                <span className="tabular ml-auto font-semibold">{formatCount(value(hovered))}</span>
              </li>
            ))}
            <li className="flex items-center gap-2 border-t pt-1">
              <span className="text-muted-foreground">All decisions</span>
              <span className="tabular ml-auto font-semibold">{formatCount(totals[hover])}</span>
            </li>
          </ul>
        </div>
      )}

      {/* The same figures as a table, for screen readers: a drawing cannot be read aloud.
          The wrapper hides it from view; a table ignores a height of its own, a div does not. */}
      <div className="sr-only">
        <table>
          <caption>Decisions per {bucketLabel(bucketSeconds)}</caption>
          <thead>
            <tr>
              <th scope="col">From</th>
              <th scope="col">Approved</th>
              <th scope="col">Step-up</th>
              <th scope="col">Declined</th>
            </tr>
          </thead>
          <tbody>
            {series.map((point) => (
              <tr key={point.at}>
                <th scope="row">{(spansDays ? formatDayHourMinute : formatHourMinute)(point.at)}</th>
                <td>{point.approve}</td>
                <td>{point.stepUp}</td>
                <td>{point.decline}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}

/** "minute" for 60 seconds, "15 minutes" for 900. */
export function bucketLabel(bucketSeconds: number): string {
  const minutes = Math.round(bucketSeconds / 60);
  return minutes === 1 ? "minute" : `${minutes} minutes`;
}

/** The element's width in pixels, kept up to date as the page is resized. 0 until measured. */
function useWidth(ref: { current: HTMLElement | null }): number {
  const [width, setWidth] = useState(0);
  useEffect(() => {
    const element = ref.current;
    if (!element) return;
    const observer = new ResizeObserver(([entry]) => setWidth(Math.floor(entry.contentRect.width)));
    observer.observe(element);
    return () => observer.disconnect();
  }, [ref]);
  return width;
}

/**
 * The top of the scale: the busiest bucket rounded up to 1, 2, 4, 6, 8 or 10 times a power of
 * ten (37 becomes 40, 130 becomes 200). Those tops all halve to a whole number, so the middle
 * gridline has a clean label.
 */
function roundUp(count: number): number {
  const power = 10 ** Math.floor(Math.log10(count));
  const lead = count / power;
  const step = [1, 2, 4, 6, 8, 10].find((s) => lead <= s) ?? 10;
  return step * power;
}

/**
 * Which buckets get a time label under them: those that start on a round clock time (every 5
 * minutes for a quarter of an hour, every 15 for an hour, every 4 hours for a day), in the
 * reader's own time zone. At most six or seven labels, so they never crowd each other.
 */
function timeLabels(series: DashboardPoint[], bucketSeconds: number): number[] {
  const spanMinutes = (series.length * bucketSeconds) / 60;
  const every = [1, 5, 15, 30, 60, 120, 240, 360, 720].find((step) => spanMinutes / step <= 6) ?? 1440;
  const labelled: number[] = [];
  series.forEach((point, i) => {
    const at = new Date(point.at);
    if ((at.getHours() * 60 + at.getMinutes()) % every === 0) labelled.push(i);
  });
  return labelled;
}
