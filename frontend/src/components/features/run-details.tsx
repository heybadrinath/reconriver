"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import type { ColumnDef } from "@tanstack/react-table";
import { RefreshCw, Download, ListFilter, LoaderCircle } from "lucide-react";
import Link from "next/link";
import { useRouter } from "next/navigation";

import { PageIntro } from "@/components/layout/page-intro";
import { ReconciliationRiver } from "@/components/river/reconciliation-river";
import { ApiErrorState } from "@/components/states/api-state";
import { StatusBadge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { DataTable } from "@/components/ui/data-table";
import { Progress } from "@/components/ui/progress";
import { api } from "@/lib/api/client";
import type { ResultView } from "@/lib/api/types";
import {
  formatCount,
  formatDuration,
  formatMoney,
  titleCase,
} from "@/lib/utils";

export function resultInspection(result: ResultView) {
  const resolutionStatus = result.resolutionStatus.toUpperCase();
  if (resolutionStatus === "OPEN" || resolutionStatus === "REOPENED") {
    return {
      href: `/exceptions?selected=${result.id}`,
      label: resolutionStatus === "REOPENED" ? "Reopened" : "Open",
    };
  }
  return {
    href: `/transactions/${result.internalTransactionId ?? result.processorTransactionId ?? result.id}`,
    label: "Trace",
  };
}

const terminalStatuses = new Set([
  "COMPLETED",
  "FAILED",
  "STOPPED",
  "ABANDONED",
]);

const resultColumns: ColumnDef<ResultView>[] = [
  {
    accessorKey: "outcome",
    header: "Outcome",
    cell: ({ getValue }) => <StatusBadge value={String(getValue())} />,
  },
  {
    accessorKey: "internalPaymentReference",
    header: "Reference",
    cell: ({ row }) => (
      <Link
        href={`/transactions/${row.original.internalTransactionId ?? row.original.processorTransactionId ?? row.original.id}`}
        className="inline-flex min-h-11 items-center font-mono text-xs font-semibold underline-offset-4 hover:underline md:min-h-0"
      >
        {row.original.internalPaymentReference ??
          row.original.processorTransactionReference ??
          row.original.bankEntryReference ??
          row.original.id}
      </Link>
    ),
  },
  {
    accessorKey: "ruleCode",
    header: "Rule",
    cell: ({ getValue }) => (
      <span className="font-mono text-[0.68rem]">
        {titleCase(String(getValue()))}
      </span>
    ),
  },
  {
    accessorKey: "grossAmount",
    header: "Gross",
    cell: ({ row }) =>
      formatMoney(row.original.grossAmount, row.original.currency),
  },
  {
    accessorKey: "processorFee",
    header: "Fee",
    cell: ({ row }) =>
      formatMoney(row.original.processorFee, row.original.currency),
  },
  {
    accessorKey: "difference",
    header: "Difference",
    cell: ({ row }) => (
      <span
        className={
          Number(row.original.difference ?? 0) !== 0
            ? "font-bold text-[var(--coral-strong)]"
            : ""
        }
      >
        {formatMoney(row.original.difference, row.original.currency)}
      </span>
    ),
  },
  {
    id: "action",
    header: "Inspect",
    enableSorting: false,
    cell: ({ row }) => {
      const inspection = resultInspection(row.original);
      return (
        <Button asChild size="sm" variant="secondary">
          <Link href={inspection.href}>{inspection.label}</Link>
        </Button>
      );
    },
  },
];

function Distribution({
  title,
  values,
  tone,
}: {
  title: string;
  values: Record<string, number>;
  tone: string;
}) {
  const entries = Object.entries(values).sort((a, b) => b[1] - a[1]);
  const max = Math.max(...entries.map(([, value]) => value), 1);
  return (
    <Card>
      <CardContent>
        <h2 className="font-display text-3xl font-bold uppercase">{title}</h2>
        <div className="mt-5 space-y-4" role="list" aria-label={title}>
          {entries.length === 0 ? (
            <p className="text-sm text-[var(--muted)]">
              No distribution data was returned.
            </p>
          ) : (
            entries.map(([label, value]) => (
              <div key={label} role="listitem">
                <div className="mb-1.5 flex justify-between gap-4 text-xs">
                  <span className="font-bold">{titleCase(label)}</span>
                  <span className="font-mono">{formatCount(value)}</span>
                </div>
                <div className="h-3 overflow-hidden rounded-full bg-[var(--surface-muted)]">
                  <div
                    className="h-full rounded-full"
                    style={{
                      width: `${(value / max) * 100}%`,
                      backgroundColor: tone,
                    }}
                  />
                </div>
              </div>
            ))
          )}
        </div>
      </CardContent>
    </Card>
  );
}

export function RunDetails({ runId }: { runId: string }) {
  const router = useRouter();
  const queryClient = useQueryClient();
  const run = useQuery({
    queryKey: ["run", runId],
    queryFn: () => api.run(runId),
  });
  const progress = useQuery({
    queryKey: ["run-progress", runId],
    queryFn: () => api.progress(runId),
    refetchInterval(query) {
      const status = query.state.data?.status?.toUpperCase();
      return status && terminalStatuses.has(status) ? false : 1_000;
    },
  });
  const status = progress.data?.status ?? run.data?.status ?? "STARTING";
  const terminal = terminalStatuses.has(status.toUpperCase());
  const completed = status.toUpperCase() === "COMPLETED";
  const summary = useQuery({
    queryKey: ["run-summary", runId],
    queryFn: () => api.summary(runId),
    enabled: completed,
  });
  const results = useQuery({
    queryKey: ["run-results", runId],
    queryFn: () => api.results(runId, 0, 100),
    enabled: completed,
  });
  const restart = useMutation({
    mutationFn: () => api.restartRun(runId),
    async onSuccess(restarted) {
      await queryClient.invalidateQueries({ queryKey: ["runs"] });
      router.push(`/runs/${restarted.id}`);
    },
  });

  if (run.error)
    return (
      <ApiErrorState error={run.error} onRetry={() => void run.refetch()} />
    );
  const percent = progress.data?.percent ?? run.data?.progressPercent ?? 0;
  const read = progress.data?.readCount ?? run.data?.processedItems ?? 0;
  const total = progress.data?.total ?? run.data?.totalWorkItems ?? 0;
  const resultItems = results.data?.items ?? [];
  const failed = status.toUpperCase() === "FAILED";
  const restartable =
    progress.data?.restartable ?? run.data?.restartable ?? false;
  const failureMessage =
    progress.data?.failureMessage ?? run.data?.failureMessage;

  return (
    <>
      <PageIntro
        eyebrow={`Run · ${runId}`}
        title="Run details"
        description="This screen polls the Spring Batch execution itself. Progress, counters, totals and results are never synthesized in the browser."
        actions={
          <>
            {restartable ? (
              <Button
                variant="coral"
                onClick={() => restart.mutate()}
                disabled={restart.isPending}
              >
                <RefreshCw size={18} aria-hidden="true" />
                Restart job
              </Button>
            ) : null}
            {completed ? (
              <Button
                variant="secondary"
                onClick={() => void api.downloadReport(runId)}
              >
                <Download size={18} aria-hidden="true" />
                Export CSV
              </Button>
            ) : null}
          </>
        }
      />

      {restart.error ? (
        <div className="mb-3">
          <ApiErrorState
            error={restart.error}
            onRetry={() => restart.mutate()}
          />
        </div>
      ) : null}
      {progress.error ? (
        <div className="mb-3">
          <ApiErrorState
            error={progress.error}
            onRetry={() => void progress.refetch()}
          />
        </div>
      ) : null}

      <div className="data-grid mb-3">
        <Card className="col-span-12 bg-[var(--ink)] text-white lg:col-span-6">
          <CardContent>
            <div className="flex items-start justify-between gap-3">
              <div>
                <p className="utility-label text-white/45">
                  Spring Batch execution
                </p>
                <div className="mt-3">
                  <StatusBadge value={status} />
                </div>
              </div>
              {!terminal ? (
                <LoaderCircle
                  className="animate-spin motion-reduce:animate-none"
                  size={28}
                  aria-hidden="true"
                />
              ) : null}
            </div>
            <div className="mt-12 flex items-end justify-between gap-4">
              <p className="metric-number">{Math.round(percent)}%</p>
              <p className="pb-1 text-sm text-white/55">
                {formatCount(read)} / {formatCount(total)} records
              </p>
            </div>
            <Progress
              value={percent}
              className="mt-4 border-white/15 bg-white/10"
            />
            <div className="mt-5 grid grid-cols-3 gap-3 border-t border-white/10 pt-5">
              <div>
                <p className="utility-label text-white/45">Read</p>
                <p className="mt-1 font-display text-2xl font-bold">
                  {formatCount(progress.data?.readCount)}
                </p>
              </div>
              <div>
                <p className="utility-label text-white/45">Written</p>
                <p className="mt-1 font-display text-2xl font-bold">
                  {formatCount(progress.data?.writeCount)}
                </p>
              </div>
              <div>
                <p className="utility-label text-white/45">Skipped</p>
                <p className="mt-1 font-display text-2xl font-bold text-[var(--coral)]">
                  {formatCount(progress.data?.skipCount)}
                </p>
              </div>
            </div>
          </CardContent>
        </Card>
        {[
          ["Matched", summary.data?.matchedCount, "bg-[var(--green-soft)]"],
          [
            "Exceptions",
            summary.data?.exceptionCount,
            "bg-[var(--coral-soft)]",
          ],
          [
            "Duration",
            formatDuration(summary.data?.durationMillis),
            "bg-[var(--yellow-soft)]",
          ],
        ].map(([label, value, tone]) => (
          <Card
            key={String(label)}
            className={`col-span-12 sm:col-span-4 lg:col-span-2 ${String(tone)}`}
          >
            <CardContent>
              <p className="utility-label text-[var(--muted)]">{label}</p>
              <p className="mt-10 font-display text-4xl leading-none font-bold">
                {typeof value === "number"
                  ? formatCount(value)
                  : (value ?? "—")}
              </p>
            </CardContent>
          </Card>
        ))}
      </div>

      {failed ? (
        <Card className="mb-3 border-[#d58e84] bg-[var(--coral-soft)]">
          <CardContent className="flex flex-col justify-between gap-5 sm:flex-row sm:items-center">
            <div>
              <StatusBadge value="FAILED" />
              <h2 className="mt-3 font-display text-4xl font-bold uppercase">
                The batch job stopped
              </h2>
              <p className="mt-2 max-w-3xl text-sm leading-6 text-[var(--muted-strong)]">
                {failureMessage ??
                  "The worker did not return a failure description."}
              </p>
              <p className="mt-2 text-xs text-[var(--muted)]">
                {restartable
                  ? "A committed checkpoint is available. Restart will continue safely."
                  : "This execution is not restartable; review the source imports before creating a new run."}
              </p>
            </div>
            {restartable ? (
              <Button onClick={() => restart.mutate()}>
                <RefreshCw size={18} aria-hidden="true" />
                Restart from checkpoint
              </Button>
            ) : null}
          </CardContent>
        </Card>
      ) : null}

      {summary.error ? (
        <ApiErrorState
          error={summary.error}
          onRetry={() => void summary.refetch()}
        />
      ) : summary.data ? (
        <>
          <Card className="mb-3">
            <CardContent>
              <ReconciliationRiver
                results={resultItems}
                onSelect={(flow) =>
                  router.push(
                    `/transactions/${flow.internalId ?? flow.processorId ?? flow.resultId}`,
                  )
                }
              />
            </CardContent>
          </Card>
          <div className="grid gap-3 lg:grid-cols-2">
            <Distribution
              title="Exception distribution"
              values={summary.data.outcomes}
              tone="var(--coral)"
            />
            <Distribution
              title="Rule distribution"
              values={summary.data.rules}
              tone="var(--violet)"
            />
          </div>
          <section className="mt-3">
            <Card>
              <CardContent>
                <div className="mb-5 flex flex-col justify-between gap-3 sm:flex-row sm:items-end">
                  <div>
                    <p className="utility-label text-[var(--muted)]">
                      Explainable output
                    </p>
                    <h2 className="mt-1 font-display text-4xl font-bold uppercase">
                      Reconciliation results
                    </h2>
                  </div>
                  <Button asChild variant="secondary">
                    <Link href="/exceptions">
                      <ListFilter
                        size={18}

                        aria-hidden="true"
                      />
                      Open workbench
                    </Link>
                  </Button>
                </div>
                {results.error ? (
                  <ApiErrorState
                    error={results.error}
                    onRetry={() => void results.refetch()}
                  />
                ) : (
                  <DataTable
                    data={resultItems}
                    columns={resultColumns}
                    caption="Reconciliation results"
                    emptyMessage="This completed run returned no result rows."
                  />
                )}
              </CardContent>
            </Card>
          </section>
        </>
      ) : !terminal ? (
        <Card className="bg-[var(--surface-muted)]">
          <CardContent className="flex items-center gap-3">
            <LoaderCircle
              className="animate-spin motion-reduce:animate-none"
              size={24}
              aria-hidden="true"
            />
            <div>
              <p className="font-bold">Reconciliation is still running</p>
              <p className="text-sm text-[var(--muted)]">
                Summary, distributions and results appear after the batch job
                reaches a terminal state.
              </p>
            </div>
          </CardContent>
        </Card>
      ) : null}
    </>
  );
}
