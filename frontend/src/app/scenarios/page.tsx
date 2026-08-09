"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  ArrowRight,
  Check,
  Download,
  FileSpreadsheet,
  LoaderCircle,
} from "lucide-react";
import { useRouter } from "next/navigation";
import { useState } from "react";

import { PageIntro } from "@/components/layout/page-intro";
import { ReconcileProgress } from "@/components/layout/reconcile-progress";
import { WorkspaceGate } from "@/components/layout/workspace-gate";
import { ApiErrorState, EmptyState } from "@/components/states/api-state";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { CardSkeleton } from "@/components/ui/skeleton";
import { api } from "@/lib/api/client";
import { cn, formatCount, titleCase } from "@/lib/utils";

const tones = [
  { bar: "bg-[var(--green)]", chip: "bg-[var(--green-soft)]" },
  { bar: "bg-[var(--coral)]", chip: "bg-[var(--coral-soft)]" },
  { bar: "bg-[var(--yellow)]", chip: "bg-[var(--yellow-soft)]" },
  { bar: "bg-[var(--violet)]", chip: "bg-[var(--violet-soft)]" },
];

export default function ScenariosPage() {
  return (
    <WorkspaceGate>
      <ScenarioSelector />
    </WorkspaceGate>
  );
}

function ScenarioSelector() {
  const router = useRouter();
  const queryClient = useQueryClient();
  const [selectedSlug, setSelectedSlug] = useState<string | null>(null);
  const scenarios = useQuery({
    queryKey: ["scenarios"],
    queryFn: api.scenarios,
  });
  const items = scenarios.data?.items ?? [];
  const effectiveSlug = selectedSlug ?? items[0]?.slug ?? null;
  const detail = useQuery({
    queryKey: ["scenario", effectiveSlug],
    queryFn: () => api.scenario(effectiveSlug ?? ""),
    enabled: Boolean(effectiveSlug),
  });
  const load = useMutation({
    mutationFn: (slug: string) => api.loadScenario(slug),
    async onSuccess() {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ["imports"] }),
        queryClient.invalidateQueries({ queryKey: ["runs"] }),
      ]);
      router.push("/imports?loaded=1");
    },
  });

  const selected =
    detail.data ?? items.find((item) => item.slug === effectiveSlug) ?? null;

  return (
    <>
      <PageIntro
        eyebrow="Reconcile · Step 1 of 3"
        title="Choose data"
        description="Start with a prepared payment scenario, then review the ledger, processor and bank sources before reconciliation."
      />
      <ReconcileProgress current={1} completedThrough={0} />

      {selected ? (
        <div className="mb-3 flex flex-col gap-4 rounded-[var(--radius-card)] border border-[var(--border)] bg-[var(--surface)] p-4 shadow-[var(--shadow-card)] sm:flex-row sm:items-center sm:justify-between sm:px-5">
          <div className="min-w-0">
            <p className="utility-label text-[var(--muted)]">
              Selected scenario
            </p>
            <p className="mt-1 truncate text-sm font-bold">{selected.name}</p>
          </div>
          <Button
            variant="primary"
            disabled={load.isPending}
            onClick={() => load.mutate(selected.slug)}
          >
            {load.isPending ? (
              <LoaderCircle
                className="animate-spin"
                size={18}
                aria-hidden="true"
              />
            ) : (
              <ArrowRight size={18} aria-hidden="true" />
            )}
            Load sources
          </Button>
        </div>
      ) : null}

      {load.error && selected ? (
        <div className="mb-3">
          <ApiErrorState
            error={load.error}
            onRetry={() => load.mutate(selected.slug)}
          />
        </div>
      ) : null}

      {scenarios.isPending ? (
        <div className="grid gap-3 md:grid-cols-2 xl:grid-cols-4">
          {Array.from({ length: 4 }).map((_, index) => (
            <CardSkeleton key={index} />
          ))}
        </div>
      ) : scenarios.error ? (
        <ApiErrorState
          error={scenarios.error}
          onRetry={() => void scenarios.refetch()}
        />
      ) : items.length === 0 ? (
        <EmptyState
          title="No scenarios are available"
          description="Prepared datasets are temporarily unavailable. Retry in a moment."
          action={
            <Button onClick={() => void scenarios.refetch()}>Retry</Button>
          }
        />
      ) : (
        <div
          className="grid gap-3 md:grid-cols-2 xl:grid-cols-4"
          role="radiogroup"
          aria-label="Prepared scenarios"
        >
          {items.map((scenario, index) => (
            <label
              key={scenario.slug}
              className={cn(
                "group relative min-h-64 cursor-pointer overflow-hidden rounded-[var(--radius-card)] border bg-[var(--surface)] p-5 text-left shadow-[var(--shadow-card)] transition-colors has-[:focus-visible]:ring-3 has-[:focus-visible]:ring-[var(--focus)]",
                effectiveSlug === scenario.slug
                  ? "border-[var(--ink)] ring-1 ring-[var(--ink)]"
                  : "border-[var(--border)] hover:border-[var(--border-strong)]",
              )}
            >
              <input
                type="radio"
                name="scenario"
                value={scenario.slug}
                checked={effectiveSlug === scenario.slug}
                onChange={() => setSelectedSlug(scenario.slug)}
                className="sr-only"
              />
              <span
                className={cn(
                  "absolute inset-x-0 top-0 h-1.5",
                  tones[index % tones.length]?.bar,
                )}
                aria-hidden="true"
              />
              <div className="flex items-start justify-between gap-4">
                <span
                  className={cn(
                    "utility-label rounded-full px-2.5 py-1 text-[var(--muted-strong)]",
                    tones[index % tones.length]?.chip,
                  )}
                >
                  {scenario.slug.replaceAll("-", " ")}
                </span>
                <span
                  className={cn(
                    "grid size-9 place-items-center rounded-full border",
                    effectiveSlug === scenario.slug
                      ? "border-[var(--ink)] bg-[var(--ink)] text-white"
                      : "border-[var(--border-strong)] bg-white",
                  )}
                >
                  {effectiveSlug === scenario.slug ? (
                    <Check size={18} aria-hidden="true" />
                  ) : (
                    <ArrowRight size={18} aria-hidden="true" />
                  )}
                </span>
              </div>
              <h2 className="mt-10 font-display text-4xl leading-[0.9] font-bold uppercase">
                {scenario.name}
              </h2>
              <p className="mt-3 line-clamp-3 text-sm leading-6 text-[var(--muted-strong)]">
                {scenario.purpose}
              </p>
              <dl className="mt-6 grid grid-cols-2 gap-2 border-t border-black/10 pt-4">
                <div>
                  <dt className="utility-label text-[var(--muted)]">
                    Payments
                  </dt>
                  <dd className="font-display text-2xl font-bold">
                    {formatCount(scenario.canonicalPaymentCount)}
                  </dd>
                </div>
                <div>
                  <dt className="utility-label text-[var(--muted)]">
                    Exceptions
                  </dt>
                  <dd className="font-display text-2xl font-bold">
                    {formatCount(scenario.expectedExceptionCount)}
                  </dd>
                </div>
              </dl>
            </label>
          ))}
        </div>
      )}

      {selected ? (
        <Card className="mt-3 overflow-hidden">
          <div className="grid lg:grid-cols-[.72fr_1.28fr]">
            <div className="border-b border-[var(--border)] bg-[var(--ink)] p-6 text-white sm:p-8 lg:border-r lg:border-b-0">
              <p className="utility-label text-white/50">Scenario preview</p>
              <h2 className="mt-3 font-display text-5xl leading-[0.85] font-bold uppercase">
                {selected.name}
              </h2>
              <p className="mt-5 text-sm leading-7 text-white/70">
                {selected.description ?? selected.purpose}
              </p>
              <div className="mt-8 rounded-2xl border border-white/10 bg-white/5 p-4 text-xs leading-6 text-white/70">
                {selected.controlledFailureAfterChunks ? (
                  <>
                    This recovery exercise intentionally interrupts its first
                    run after {selected.controlledFailureAfterChunks} chunks.
                    Restart the failed run to continue from its last checkpoint.
                  </>
                ) : (
                  <>
                    Expected totals accompany the scenario. Reconciliation
                    recalculates every outcome from the loaded source rows.
                  </>
                )}
              </div>
            </div>
            <div className="p-5 sm:p-8">
              <div className="flex items-center justify-between gap-4">
                <div>
                  <p className="utility-label text-[var(--muted)]">
                    Input files
                  </p>
                  <h3 className="mt-1 font-display text-3xl font-bold uppercase">
                    Inspect before loading
                  </h3>
                </div>
                {detail.isFetching ? (
                  <LoaderCircle
                    className="animate-spin"
                    size={22}
                    aria-label="Loading scenario details"
                  />
                ) : null}
              </div>
              <details className="group mt-5 rounded-2xl border border-[var(--border)] bg-[var(--surface-muted)]">
                <summary className="flex min-h-12 cursor-pointer list-none items-center justify-between gap-4 rounded-2xl px-4 text-sm font-bold focus-visible:ring-3 focus-visible:ring-[var(--focus)] focus-visible:outline-none [&::-webkit-details-marker]:hidden">
                  <span>
                    View {formatCount(selected.availableFiles?.length ?? 0)}{" "}
                    input
                    {(selected.availableFiles?.length ?? 0) === 1
                      ? " file"
                      : " files"}
                  </span>
                  <span className="font-mono text-xs text-[var(--muted)] group-open:hidden">
                    Expand
                  </span>
                  <span className="hidden font-mono text-xs text-[var(--muted)] group-open:inline">
                    Collapse
                  </span>
                </summary>
                <div className="divide-y divide-[var(--border)] border-t border-[var(--border)]">
                  {(selected.availableFiles ?? []).map((file) => (
                    <div
                      key={file.fileName}
                      className="flex flex-col gap-3 p-4 sm:flex-row sm:items-center"
                    >
                      <span className="grid size-10 shrink-0 place-items-center rounded-full bg-white">
                        <FileSpreadsheet size={21} aria-hidden="true" />
                      </span>
                      <div className="min-w-0 flex-1">
                        <p className="truncate text-sm font-bold">
                          {file.fileName}
                        </p>
                        <p className="mt-1 font-mono text-[0.65rem] text-[var(--muted)]">
                          {file.sourceType
                            ? titleCase(file.sourceType)
                            : "Supporting artifact"}{" "}
                          · {formatCount(file.rowCount)} rows
                          {file.checksum
                            ? ` · SHA-256 ${file.checksum.slice(0, 12)}…`
                            : null}
                        </p>
                      </div>
                      <Button
                        variant="secondary"
                        size="sm"
                        onClick={() =>
                          void api.downloadScenarioFile(
                            selected.slug,
                            file.fileName,
                          )
                        }
                      >
                        <Download size={16} aria-hidden="true" />
                        Download
                      </Button>
                    </div>
                  ))}
                  {(selected.availableFiles ?? []).length === 0 &&
                  !detail.isFetching ? (
                    <p className="p-4 text-sm text-[var(--muted)]">
                      File details are unavailable for this scenario.
                    </p>
                  ) : null}
                </div>
              </details>
              <div className="mt-6 rounded-2xl bg-[var(--coral-soft)] p-4">
                <p className="max-w-lg text-xs leading-5 text-[var(--muted-strong)]">
                  Synthetic data only. Do not upload payment, banking, customer
                  or personal information.
                </p>
              </div>
            </div>
          </div>
        </Card>
      ) : null}
    </>
  );
}
