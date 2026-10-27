import type { RuleSetDetail } from "@/lib/types";
import { formatDateTime } from "@/lib/format";
import { ACTIVATION_LABEL } from "@/lib/rules";
import { Note } from "@/pages/AuditPage";

/** What happened to one version (from the audit log), and when it was live. */
export function VersionHistory({ d }: { d: RuleSetDetail }) {
  return (
    <div className="grid gap-8 lg:grid-cols-2">
      <section aria-labelledby="what-happened" className="space-y-3">
        <h2 id="what-happened" className="text-[16px] font-semibold">
          What happened
        </h2>
        {d.history.length === 0 ? (
          <p className="text-muted-foreground">Nothing recorded for this version.</p>
        ) : (
          // A timeline is a sequence, so it is drawn as one: newest at the top.
          <ol className="relative space-y-4 border-l pl-5">
            {d.history.map((event) => (
              <li key={event.id} className="relative">
                <span className="absolute top-1.5 -left-[25px] size-2.5 rounded-full border-2 border-card bg-foreground" aria-hidden="true" />
                <p>{event.summary}</p>
                <Note event={event} />
                <p className="text-[12.5px] text-muted-foreground">
                  {event.actorName}, <span className="tabular">{formatDateTime(event.occurredAt)}</span>
                </p>
              </li>
            ))}
          </ol>
        )}
      </section>

      <section aria-labelledby="live-periods" className="space-y-3">
        <h2 id="live-periods" className="text-[16px] font-semibold">
          When it was live
        </h2>
        {d.activations.length === 0 ? (
          <p className="text-muted-foreground">This version has never been live.</p>
        ) : (
          <ul className="space-y-1.5 text-[13px]">
            {d.activations.map((a) => (
              <li key={a.id}>
                <span className="tabular">{formatDateTime(a.activatedAt)}</span>
                <span className="text-muted-foreground">
                  {" "}
                  {ACTIVATION_LABEL[a.kind]}, by {a.activatedByName}
                </span>
              </li>
            ))}
          </ul>
        )}
      </section>
    </div>
  );
}
