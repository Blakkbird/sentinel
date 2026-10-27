import { Link } from "react-router";
import { useInfiniteQuery } from "@tanstack/react-query";
import { LoaderCircle } from "lucide-react";
import { api } from "@/lib/api";
import type { AuditEvent, AuditEventPage } from "@/lib/types";
import { formatDateTime } from "@/lib/format";
import { PageHeader } from "@/components/PageHeader";
import { EmptyState, ErrorState } from "@/components/States";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";

/** Every change to the rules: who, what and when. The database refuses to edit or delete these. */
export function AuditPage() {
  const log = useInfiniteQuery({
    queryKey: ["audit"],
    initialPageParam: null as number | null,
    queryFn: ({ pageParam }) => api<AuditEventPage>(`/api/audit?limit=50${pageParam ? `&before=${pageParam}` : ""}`),
    getNextPageParam: (last) => last.nextBefore,
  });
  const events = log.data?.pages.flatMap((page) => page.items) ?? [];

  return (
    <>
      <PageHeader
        title="Audit log"
        description="Every change to the rules: who made it and when. Entries can be added, never edited or deleted; the database itself refuses."
      />

      {log.isPending && <Skeleton className="h-72 w-full" />}
      {log.isError && <ErrorState error={log.error} onRetry={() => log.refetch()} />}
      {log.isSuccess &&
        (events.length === 0 ? (
          <EmptyState title="Nothing recorded yet">Changes to the rules appear here as they happen.</EmptyState>
        ) : (
          <>
            <div className="rounded-md border bg-card">
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead className="w-[190px]">When</TableHead>
                    <TableHead className="w-[180px]">Who</TableHead>
                    <TableHead>What happened</TableHead>
                    <TableHead className="w-[90px]">Version</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {events.map((event) => (
                    <TableRow key={event.id}>
                      <TableCell className="tabular whitespace-nowrap">{formatDateTime(event.occurredAt)}</TableCell>
                      <TableCell>
                        <span className="block">{event.actorName}</span>
                        <span className="block font-mono text-[12px] text-muted-foreground">{event.actor}</span>
                      </TableCell>
                      <TableCell>
                        <span className="block">{event.summary}</span>
                        <Note event={event} />
                      </TableCell>
                      <TableCell>
                        {event.targetType === "RULE_SET" && (
                          <Link to={`/rules/${event.targetId}`} className="font-display text-[16px] font-semibold hover:underline">
                            v{event.targetId}
                          </Link>
                        )}
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </div>
            <div className="mt-4 flex justify-center">
              {log.hasNextPage ? (
                <Button variant="outline" onClick={() => log.fetchNextPage()} disabled={log.isFetchingNextPage}>
                  {log.isFetchingNextPage && <LoaderCircle className="animate-spin" aria-hidden="true" />}
                  Load older entries
                </Button>
              ) : (
                <p className="text-[13px] text-muted-foreground">That is the whole log.</p>
              )}
            </div>
          </>
        ))}
    </>
  );
}

/** A reviewer's note, when the entry has one. */
export function Note({ event }: { event: AuditEvent }) {
  const note = event.details.note;
  return typeof note === "string" && note.trim() !== "" ? (
    <span className="mt-0.5 block text-[13px] text-muted-foreground">“{note}”</span>
  ) : null;
}
