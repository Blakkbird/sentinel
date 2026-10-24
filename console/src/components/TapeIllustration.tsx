import { cn } from "@/lib/utils";

/**
 * A drawing of the decision tape for the sign-in page. Not live data: a fixed pattern of
 * ordinary payments with one card-testing attack in the middle, generated from a seed so it
 * looks the same on every visit. The bars rise once when the page opens.
 */
type Bar = { height: number; tone: "approve" | "stepup" | "decline" };

function makeBars(count: number): Bar[] {
  let seed = 7;
  const next = () => {
    seed = (seed * 1103515245 + 12345) % 2147483648;
    return seed / 2147483648;
  };
  const bars: Bar[] = [];
  for (let i = 0; i < count; i++) {
    const attack = i >= 52 && i < 66;
    const big = !attack && next() < 0.06;
    const height = attack ? 22 + next() * 14 : big ? 88 + next() * 12 : 48 + next() * 30;
    const tone = attack ? "decline" : big ? "stepup" : "approve";
    bars.push({ height, tone });
  }
  return bars;
}

const BARS = makeBars(84);

// Fixed colours: the panel is dark navy in both light and dark mode.
const TONE = { approve: "bg-[#3bbf8a]", stepup: "bg-[#e3a23b]", decline: "bg-[#f26b50]" };

export function TapeIllustration({ className }: { className?: string }) {
  return (
    <div aria-hidden="true" className={cn("flex h-40 items-end gap-[3px]", className)}>
      {BARS.map((bar, i) => (
        <span
          key={i}
          className={cn("w-[5px] shrink-0 origin-bottom animate-rise rounded-[1px]", TONE[bar.tone])}
          style={{ height: `${bar.height}%`, animationDelay: `${i * 9}ms` }}
        />
      ))}
    </div>
  );
}
