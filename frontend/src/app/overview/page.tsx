"use client";

import { useQuery } from "@tanstack/react-query";
import { ArrowRight, Clock, LoaderCircle } from "lucide-react";
import Link from "next/link";
import { useRouter } from "next/navigation";

import { PageIntro } from "@/components/layout/page-intro";
import { WorkspaceGate } from "@/components/layout/workspace-gate";
import { ReconciliationRiver } from "@/components/river/reconciliation-river";
import { ApiErrorState, EmptyState } from "@/components/states/api-state";
import { StatusBadge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { api } from "@/lib/api/client";
import type { ResultView } from "@/lib/api/types";
import {
  formatCount,
  formatDate,
  formatDuration,
  formatMoney,
  titleCase,
} from "@/lib/utils";

export default function OverviewPage() {
  return (
    <WorkspaceGate>
      <OverviewContent />
    </WorkspaceGate>
  );
}

function ageBuckets(results: ResultView[]) {
  const buckets = {
    "Same day": 0,
    "1–2 days": 0,
    "3–5 days": 0,
    "Over 5 days": 0,
    "Awaiting bank": 0,
  };
  results.forEach((result) => {
    if (!result.processorEventAt || !result.bankBookedAt) {
      buckets["Awaiting bank"] += 1;
      return;
    }
    const days =
      (new Date(result.bankBookedAt).getTime() -
        new Date(result.processorEventAt).getTime()) /
      86_400_000;
    if (days < 1) buckets["Same day"] += 1;
    else if (days <= 2) buckets["1–2 days"] += 1;
    else if (days <= 5) buckets["3–5 days"] += 1;
    else buckets["Over 5 days"] += 1;
  });
  return buckets;
}

function OverviewContent() {
  const router = useRouter();
  const runs = useQuery({ queryKey: ["runs"], queryFn: () => api.runs(0, 10) });
  const latest = runs.data?.items.find(
    (run) => run.status.toUpperCase() === "COMPLETED",
  );
  const summary = useQuery({
    queryKey: ["run-summary", latest?.id],
    queryFn: () => api.summary(latest?.id ?? ""),
    enabled: Boolean(latest),
  });
  const results = useQuery({
    queryKey: ["run-results", latest?.id],
    queryFn: () => api.results(latest?.id ?? "", 0, 100),
    enabled: Boolean(latest),
  });

  if (runs.isPending)
    return (
      <div className="grid min-h-[55vh] place-items-center" role="status">
        <LoaderCircle className="animate-spin" size={32} aria-hidden="true" />
        <span className="sr-only">Loading workspace overview</span>
      </div>
    );
  if (runs.error)
    return (
      <ApiErrorState error={runs.error} onRetry={() => void runs.refetch()} />
    );

  const runList = runs.data?.items ?? [];
  if (!latest) {
    return (
      <>
        <PageIntro
          eyebrow="Workspace dashboard"
          title="Reconciliation overview"
          description="Choose a prepared data set and complete reconciliation to see totals, exception exposure and settlement flows."
        />
        <EmptyState
          title="No completed run yet"
          description="Choose a scenario, review its three source files and run reconciliation to populate this dashboard."
          action={
            <Button asChild>
              <Link href="/scenarios">
                Choose a scenario <ArrowRight size={18} aria-hidden="true" />
              </Link>
            </Button>
          }
        />
      </>
    );
  }
  if (summary.error)
    return (
      <ApiErrorState
        error={summary.error}
        onRetry={() => void summary.refetch()}
      />
    );
  const data = summary.data;
  const resultItems = results.data?.items ?? [];
  const agingSample = ageBuckets(resultItems);
  const maxOutcome = Math.max(...Object.values(data?.outcomes ?? {}), 1);
  const maxAge = Math.max(...Object.values(agingSample), 1);
  const sampledResultCount = resultItems.length;
  const totalResultCount = results.data?.totalElements ?? sampledResultCount;

  return (
    <>
      <PageIntro
        eyebrow={`Latest completed run · ${latest.id}`}
        title="Reconciliation overview"
        description="Latest reconciliation totals, exception exposure and settlement timing. Open any flow to inspect the decision evidence."
        actions={
          <Button asChild variant="secondary">
            <Link href={`/runs/${latest.id}`}>
              Open run <ArrowRight size={18} aria-hidden="true" />
            </Link>
          </Button>
        }
      />

      <div className="data-grid mb-3">
        <Card className="col-span-12 md:col-span-7">
          <CardContent>
            <div className="flex items-start justify-between gap-4">
              <div>
                <p className="utility-label text-[var(--muted)]">
                  Total processed
                </p>
                <p className="metric-number mt-4">
                  {formatCount(data?.totalResults)}
                </p>
              </div>
              <span className="rounded-full bg-[var(--green-soft)] px-3 py-1 text-sm font-bold">
                {data ? `${data.matchedRate.toFixed(1)}% matched` : "—"}
              </span>
            </div>
            <div className="mt-10 grid grid-cols-2 gap-3 sm:grid-cols-3">
              <div className="rounded-2xl border border-[var(--border)] bg-[var(--surface-muted)] p-4">
                <p className="utility-label text-[var(--muted)]">Matched</p>
                <p className="mt-2 font-display text-3xl font-bold">
                  {formatCount(data?.matchedCount)}
                </p>
                <div className="mt-4 h-2 rounded-full bg-white">
                  <div
                    className="h-full rounded-full bg-[var(--green)]"
                    style={{ width: `${data?.matchedRate ?? 0}%` }}
                  />
                </div>
              </div>
              <div className="rounded-2xl border border-[var(--border)] bg-[var(--surface-muted)] p-4">
                <p className="utility-label text-[var(--muted)]">Exceptions</p>
                <p className="mt-2 font-display text-3xl font-bold">
                  {formatCount(data?.exceptionCount)}
                </p>
                <div className="mt-4 h-2 rounded-full bg-white">
                  <div
                    className="h-full rounded-full bg-[var(--coral)]"
                    style={{ width: `${100 - (data?.matchedRate ?? 100)}%` }}
                  />
                </div>
              </div>
              <div className="col-span-2 rounded-2xl border border-[var(--border)] bg-[var(--surface-muted)] p-4 sm:col-span-1">
                <p className="utility-label text-[var(--muted)]">Duration</p>
                <p className="mt-2 font-display text-3xl font-bold">
                  {formatDuration(data?.durationMillis)}
                </p>
              </div>
            </div>
          </CardContent>
        </Card>
        <Card className="col-span-12 bg-[var(--ink)] text-white md:col-span-5">
          <CardContent>
            <p className="utility-label text-white/45">Reconciliation health</p>
            <div className="mt-8 flex items-end justify-between gap-4">
              <div>
                <p className="metric-number text-[var(--coral)]">
                  {formatCount(data?.openExceptions)}
                </p>
                <p className="mt-2 text-sm text-white/60">open exceptions</p>
              </div>
              <Button asChild variant="secondary">
                <Link href="/exceptions">
                  Investigate <ArrowRight size={18} aria-hidden="true" />
                </Link>
              </Button>
            </div>
            <div className="mt-8 border-t border-white/10 pt-5">
              <p className="utility-label text-white/45">Unreconciled value</p>
              <p className="mt-2 font-display text-4xl font-bold">
                {formatMoney(data?.unreconciledValue)}
              </p>
            </div>
          </CardContent>
        </Card>
        <Card className="col-span-6 overflow-hidden md:col-span-3">
          <div className="h-1.5 bg-[var(--yellow)]" aria-hidden="true" />
          <CardContent>
            <p className="utility-label text-[var(--muted)]">
              Settlement batches
            </p>
            <p className="metric-number mt-10">
              {formatCount(data?.settlementBatchCount)}
            </p>
          </CardContent>
        </Card>
        <Card className="col-span-6 overflow-hidden md:col-span-3">
          <div className="h-1.5 bg-[var(--green)]" aria-hidden="true" />
          <CardContent>
            <p className="utility-label text-[var(--muted)]">Run duration</p>
            <p className="mt-10 font-display text-5xl leading-none font-bold">
              {formatDuration(data?.durationMillis)}
            </p>
          </CardContent>
        </Card>
        <Card className="col-span-12 overflow-hidden md:col-span-6">
          <div className="h-1.5 bg-[var(--violet)]" aria-hidden="true" />
          <CardContent>
            <p className="utility-label text-[var(--muted)]">Operator queue</p>
            <div className="mt-8 flex items-end justify-between gap-4">
              <div>
                <p className="font-display text-5xl font-bold">
                  {formatCount(data?.openExceptions)}
                </p>
                <p className="text-sm text-[var(--muted)]">
                  exceptions need a decision
                </p>
              </div>
              <Button asChild variant="primary">
                <Link href="/exceptions">
                  Investigate <ArrowRight size={18} aria-hidden="true" />
                </Link>
              </Button>
            </div>
          </CardContent>
        </Card>
      </div>

      {results.error ? (
        <ApiErrorState
          error={results.error}
          onRetry={() => void results.refetch()}
        />
      ) : (
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
      )}

      <div className="grid gap-3 lg:grid-cols-2">
        <Card>
          <CardContent>
            <p className="utility-label text-[var(--muted)]">Actual outcomes</p>
            <h2 className="mt-2 font-display text-4xl font-bold uppercase">
              Exception breakdown
            </h2>
            <div className="mt-6 space-y-4">
              {Object.entries(data?.outcomes ?? {})
                .sort((a, b) => b[1] - a[1])
                .map(([label, value], index) => (
                  <div key={label}>
                    <div className="mb-1.5 flex justify-between text-xs">
                      <span className="font-bold">{titleCase(label)}</span>
                      <span className="font-mono">{formatCount(value)}</span>
                    </div>
                    <div className="h-3 rounded-full bg-[var(--surface-muted)]">
                      <div
                        className="h-full rounded-full"
                        style={{
                          width: `${(value / maxOutcome) * 100}%`,
                          backgroundColor:
                            index % 2 ? "var(--violet)" : "var(--coral)",
                        }}
                      />
                    </div>
                  </div>
                ))}
            </div>
          </CardContent>
        </Card>
        <Card>
          <CardContent>
            <p className="utility-label text-[var(--muted)]">
              Displayed result sample · processor event → bank booking
            </p>
            <h2 className="mt-2 font-display text-4xl font-bold uppercase">
              Settlement aging sample
            </h2>
            <div className="mt-6 space-y-4">
              {Object.entries(agingSample).map(([label, value]) => (
                <div key={label}>
                  <div className="mb-1.5 flex justify-between text-xs">
                    <span className="font-bold">{label}</span>
                    <span className="font-mono">{formatCount(value)}</span>
                  </div>
                  <div className="h-3 rounded-full bg-[var(--surface-muted)]">
                    <div
                      className="h-full rounded-full bg-[var(--yellow)]"
                      style={{ width: `${(value / maxAge) * 100}%` }}
                    />
                  </div>
                </div>
              ))}
            </div>
            <p className="mt-5 text-xs leading-5 text-[var(--muted)]">
              Based on {formatCount(sampledResultCount)} of{" "}
              {formatCount(totalResultCount)} results loaded in this view. Open
              the run for the authoritative paginated result set.
            </p>
          </CardContent>
        </Card>
      </div>

      <Card className="mt-3">
        <CardContent>
          <div className="mb-5 flex items-end justify-between">
            <div>
              <p className="utility-label text-[var(--muted)]">Batch history</p>
              <h2 className="mt-1 font-display text-4xl font-bold uppercase">
                Recent runs
              </h2>
            </div>
            <Button asChild variant="secondary">
              <Link href="/runs">All runs</Link>
            </Button>
          </div>
          <div className="divide-y divide-[var(--border)] rounded-2xl border border-[var(--border)]">
            {runList.map((run) => (
              <Link
                key={run.id}
                href={`/runs/${run.id}`}
                className="flex flex-col gap-3 p-4 hover:bg-[var(--surface-muted)] focus-visible:ring-3 focus-visible:ring-[var(--focus)] focus-visible:outline-none focus-visible:ring-inset sm:flex-row sm:items-center"
              >
                <Clock size={20} aria-hidden="true" />
                <span className="min-w-0 flex-1">
                  <span className="block truncate font-mono text-xs">
                    {run.id}
                  </span>
                  <span className="mt-1 block text-xs text-[var(--muted)]">
                    {formatDate(run.createdAt)}
                  </span>
                </span>
                <StatusBadge value={run.status} />
                <span className="font-display text-2xl font-bold">
                  {formatCount(run.matchedCount)} matched
                </span>
                <ArrowRight size={18} aria-hidden="true" />
              </Link>
            ))}
          </div>
        </CardContent>
      </Card>
    </>
  );
}
