const FILL: Record<string, string> = { A: "fill-approve", S: "fill-stepup", D: "fill-decline" };

/**
 * The replay strip: the same idea as the live tape, for the past. One thin column per decision,
 * oldest on the left. A decision the new rules would leave alone is a low, faint mark in its
 * outcome's colour. A decision they would change stands full height, in its new outcome's
 * colour. So an attack the new rule catches shows up as a red cluster, and a rule that would
 * hurt ordinary customers shows up as red scattered across the whole strip.
 *
 * Drawn as one SVG stretched to the width of the page: runs of unchanged decisions become a
 * single rectangle each, which keeps 5,000 decisions down to a few hundred shapes.
 */
export function ReplayStrip({ live, next }: { live: string; next: string }) {
  const n = next.length;
  if (n === 0) return null;
  const barWidth = Math.max(1, n / 480); // never thinner than about 2px on a laptop screen

  const shapes = [];
  let i = 0;
  while (i < n) {
    if (live[i] !== next[i]) {
      i += 1;
      continue;
    }
    let j = i;
    while (j < n && live[j] === next[j] && next[j] === next[i]) j += 1;
    shapes.push(<rect key={`u${i}`} x={i} y={80} width={j - i} height={20} className={FILL[next[i]]} opacity={0.32} />);
    i = j;
  }
  for (let k = 0; k < n; k += 1) {
    if (live[k] !== next[k]) {
      shapes.push(<rect key={`c${k}`} x={k} y={0} width={barWidth} height={100} className={FILL[next[k]]} />);
    }
  }

  return (
    <svg
      viewBox={`0 0 ${n} 100`}
      preserveAspectRatio="none"
      shapeRendering="crispEdges"
      className="block h-16 w-full rounded-md border bg-card"
      role="img"
      aria-label={`${n} past decisions, oldest first. Full-height marks are the ones these rules would change.`}
    >
      {shapes}
    </svg>
  );
}
