"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import type { ColumnDef } from "@tanstack/react-table";
import { RefreshCw, ArrowRight, Play, LoaderCircle } from "lucide-react";
import Link from "next/link";
import { useRouter } from "next/navigation";

import { PageIntro } from "@/components/layout/page-intro";
import { ReconcileProgress } from "@/components/layout/reconcile-progress";
import { WorkspaceGate } from "@/components/layout/workspace-gate";
import { ApiErrorState, EmptyState } from "@/components/states/api-state";
import { StatusBadge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { DataTable } from "@/components/ui/data-table";
import { Progress } from "@/components/ui/progress";
import { api, getLoadedImportIds } from "@/lib/api/client";
import type { RunView } from "@/lib/api/types";
import {
  isImportActive,
  summarizeImportReadiness,
} from "@/lib/import-readiness";
import { formatCount, formatDate, formatDuration } from "@/lib/utils";

const runColumns: ColumnDef<RunView>[] = [
  {
    accessorKey: "id",
    header: "Run",
    cell: ({ row }) => (
      <div>
        <Link
          href={`/runs/${row.original.id}`}
          className="font-bold underline-offset-4 hover:underline"
        >
          Open run
        </Link>
        <p className="mt-1 max-w-48 truncate font-mono text-[0.65rem] text-[var(--muted)]">
          {row.original.id}
        </p>
      </div>
    ),
  },
  {
    accessorKey: "status",
    header: "Status",
    cell: ({ getValue }) => <StatusBadge value={String(getValue())} />,
  },
  {
    accessorKey: "progressPercent",
    header: "Progress",
    cell: ({ row }) => (
      <div className="min-w-32">
        <div className="mb-1 flex justify-between text-xs">
          <span>{Math.round(row.original.progressPercent)}%</span>
          <span>
            {formatCount(row.original.processedItems)} /{" "}
            {formatCount(row.original.totalWorkItems)}
          </span>
        </div>
        <Progress value={row.original.progressPercent} />
      </div>
    ),
  },
  {
    accessorKey: "matchedCount",
    header: "Matched",
    cell: ({ getValue }) => formatCount(Number(getValue())),
  },
  {
    accessorKey: "exceptionCount",
    header: "Exceptions",
    cell: ({ getValue }) => formatCount(Number(getValue())),
  },
  {
    accessorKey: "durationMillis",
    header: "Duration",
    cell: ({ getValue }) => formatDuration(getValue() as number | null),
  },
  {
    accessorKey: "createdAt",
    header: "Created",
    cell: ({ getValue }) => formatDate(String(getValue())),
  },
];

export default function RunsPage() {
  return (
    <WorkspaceGate>
      <RunsContent />
    </WorkspaceGate>
  );
}

function RunsContent() {
  const router = useRouter();
  const queryClient = useQueryClient();
  const runs = useQuery({
    queryKey: ["runs"],
    queryFn: () => api.runs(0, 100),
    refetchInterval: 5_000,
  });
  const imports = useQuery({
    queryKey: ["imports"],
    queryFn: () => api.imports(0, 100),
    refetchInterval(query) {
      return query.state.data?.items.some(isImportActive) ? 1_000 : false;
    },
  });
  const start = useMutation({
    mutationFn: api.startRun,
    async onSuccess(run) {
      await queryClient.invalidateQueries({ queryKey: ["runs"] });
      router.push(`/runs/${run.id}`);
    },
  });
  const list = runs.data?.items ?? [];
  const active = list.find((run) =>
    ["STARTING", "RUNNING"].includes(run.status.toUpperCase()),
  );
  const importReadiness = summarizeImportReadiness(
    imports.data?.items ?? [],
    getLoadedImportIds(),
  );

  return (
    <>
      <PageIntro
        eyebrow="Reconcile · Step 3 of 3"
        title="Run reconciliation"
        description="Compare every loaded record, monitor progress and resume an interrupted run from its last safe checkpoint."
        actions={
          <Button
            onClick={() => start.mutate()}
            disabled={
              start.isPending || Boolean(active) || !importReadiness.ready
            }
          >
            {start.isPending ? (
              <LoaderCircle
                className="animate-spin"
                size={18}
                aria-hidden="true"
              />
            ) : (
              <Play size={18} aria-hidden="true" />
            )}
            {active
              ? "A run is active"
              : importReadiness.active.length > 0
                ? "Imports are processing"
                : !importReadiness.ready
                  ? "Three sources required"
                  : "Run reconciliation"}
          </Button>
        }
      />
      <ReconcileProgress current={3} completedThrough={2} />

      {start.error ? (
        <div className="mb-3">
          <ApiErrorState error={start.error} onRetry={() => start.mutate()} />
        </div>
      ) : null}

      <div className="data-grid mb-3">
        <Card className="col-span-12 bg-[var(--ink)] text-white lg:col-span-7">
          <CardContent>
            <div className="flex items-start justify-between gap-4">
              <div>
                <p className="utility-label text-white/45">
                  Current reconciliation
                </p>
                <h2 className="mt-2 font-display text-4xl font-bold uppercase">
                  {active ? "Processing records" : "Ready to reconcile"}
                </h2>
              </div>
              {active ? (
                <LoaderCircle
                  className="animate-spin motion-reduce:animate-none"
                  size={28}
                  aria-hidden="true"
                />
              ) : (
                <Play size={28} aria-hidden="true" />
              )}
            </div>
            {active ? (
              <div className="mt-12">
                <div className="mb-3 flex items-end justify-between gap-4">
                  <p className="metric-number">
                    {Math.round(active.progressPercent)}%
                  </p>
                  <p className="pb-1 text-sm text-white/55">
                    {formatCount(active.processedItems)} of{" "}
                    {formatCount(active.totalWorkItems)} records
                  </p>
                </div>
                <Progress
                  value={active.progressPercent}
                  className="border-white/20 bg-white/10"
                />
                <Button asChild variant="secondary" className="mt-5">
                  <Link href={`/runs/${active.id}`}>
                    Watch this run <ArrowRight size={18} aria-hidden="true" />
                  </Link>
                </Button>
              </div>
            ) : (
              <p className="mt-12 max-w-xl text-sm leading-7 text-white/60">
                {importReadiness.ready
                  ? "Internal, processor and bank source files are ready for comparison."
                  : importReadiness.active.length > 0
                    ? `${importReadiness.readySourceCount} of 3 source types are ready. Run creation stays disabled until validation finishes.`
                    : "Load a valid internal, processor and bank source before starting reconciliation."}
              </p>
            )}
          </CardContent>
        </Card>
        <Card className="col-span-12 overflow-hidden sm:col-span-6 lg:col-span-2">
          <div className="h-1.5 bg-[var(--green)]" aria-hidden="true" />
          <CardContent className="pt-4">
            <p className="utility-label text-[var(--muted)]">Completed</p>
            <p className="metric-number mt-10">
              {formatCount(
                list.filter((run) => run.status.toUpperCase() === "COMPLETED")
                  .length,
              )}
            </p>
            <p className="mt-2 text-xs text-[var(--muted)]">
              runs in this workspace
            </p>
          </CardContent>
        </Card>
        <Card className="col-span-12 overflow-hidden sm:col-span-6 lg:col-span-3">
          <div className="h-1.5 bg-[var(--coral)]" aria-hidden="true" />
          <CardContent className="pt-4">
            <p className="utility-label text-[var(--muted)]">
              Restartable failures
            </p>
            <p className="metric-number mt-10">
              {formatCount(list.filter((run) => run.restartable).length)}
            </p>
            <p className="mt-2 text-xs text-[var(--muted)]">
              safe checkpoints available
            </p>
          </CardContent>
        </Card>
      </div>

      {runs.isPending ? (
        <Card>
          <CardContent className="flex items-center gap-3">
            <LoaderCircle
              className="animate-spin"
              size={24}
              aria-hidden="true"
            />
            <p>Loading run history…</p>
          </CardContent>
        </Card>
      ) : runs.error ? (
        <ApiErrorState error={runs.error} onRetry={() => void runs.refetch()} />
      ) : list.length === 0 ? (
        <EmptyState
          title="No reconciliation runs"
          description={
            importReadiness.ready
              ? "Validated sources are ready. Start the first reconciliation."
              : importReadiness.active.length > 0
                ? "Source validation is still running. Review imports and wait for all three sources before starting."
                : "Load or upload all three source types before starting reconciliation."
          }
          action={
            importReadiness.ready ? (
              <Button onClick={() => start.mutate()}>
                <Play size={18} aria-hidden="true" /> Start first run
              </Button>
            ) : (
              <Button asChild variant="secondary">
                <Link href="/imports">Review imports</Link>
              </Button>
            )
          }
        />
      ) : (
        <DataTable
          data={list}
          columns={runColumns}
          caption="Reconciliation run history"
        />
      )}

      {list.some((run) => run.restartable) ? (
        <Card className="mt-3 bg-[var(--yellow-soft)]">
          <CardContent className="flex flex-col justify-between gap-4 sm:flex-row sm:items-center">
            <div className="flex items-start gap-3">
              <RefreshCw
                className="mt-1 shrink-0"
                size={22}

                aria-hidden="true"
              />
              <div>
                <h2 className="font-display text-3xl font-bold uppercase">
                  A reconciliation can be resumed
                </h2>
                <p className="mt-1 text-sm text-[var(--muted-strong)]">
                  Open the failed run to review its error and continue from the
                  last committed checkpoint.
                </p>
              </div>
            </div>
            <Button asChild variant="secondary">
              <Link
                href={`/runs/${list.find((run) => run.restartable)?.id ?? ""}`}
              >
                Review failure
              </Link>
            </Button>
          </CardContent>
        </Card>
      ) : null}
    </>
  );
}
