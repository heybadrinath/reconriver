"use client";

import { useQuery } from "@tanstack/react-query";
import {
  ArrowRight,
  Database,
  Download,
  FileCode,
  FileCode2,
  Sprout,
  ShieldCheck,
} from "lucide-react";

import { AppShell } from "@/components/layout/app-shell";
import { PageIntro } from "@/components/layout/page-intro";
import { ApiErrorState } from "@/components/states/api-state";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { api } from "@/lib/api/client";
import { formatCount } from "@/lib/utils";

const datasetUrl =
  process.env.NEXT_PUBLIC_DATASET_URL ?? "https://huggingface.co/datasets";

const scenarioTones = [
  "bg-[var(--green)]",
  "bg-[var(--coral)]",
  "bg-[var(--yellow)]",
  "bg-[var(--violet)]",
];

const definitions = [
  ["Duplicate input", "A source identifier or complete file is repeated."],
  [
    "Fee mismatch",
    "The processor fee differs from the configured BigDecimal policy.",
  ],
  [
    "Missing settlement",
    "A processor settlement batch has no corresponding bank credit.",
  ],
  ["Currency mismatch", "Linked records use different ISO currency codes."],
  [
    "Late settlement",
    "A bank credit falls outside the configured settlement window.",
  ],
  [
    "Ambiguous match",
    "More than one candidate satisfies the available references.",
  ],
];

export default function DatasetPage() {
  const scenarios = useQuery({
    queryKey: ["scenarios"],
    queryFn: api.scenarios,
  });
  return (
    <AppShell>
      <PageIntro
        eyebrow="CC BY 4.0 · Deterministic Java generator"
        title="Synthetic dataset"
        description="Payment data is intensely private. This public dataset provides realistic reconciliation structure and known exceptions without exposing a customer, card or bank account."
        actions={
          <Button asChild>
            <a href={datasetUrl} target="_blank" rel="noreferrer">
              <Download size={18} aria-hidden="true" />
              Open on Hugging Face
            </a>
          </Button>
        }
      />

      <div className="data-grid mb-3">
        <Card className="col-span-12 overflow-hidden lg:col-span-7">
          <div className="h-1.5 bg-[var(--yellow)]" aria-hidden="true" />
          <CardContent className="flex min-h-80 flex-col justify-between">
            <ShieldCheck size={34} aria-hidden="true" />
            <div className="mt-14">
              <h2 className="font-display text-5xl leading-[0.85] font-extrabold uppercase sm:text-7xl">
                Useful payment edge cases, zero private records.
              </h2>
              <p className="mt-5 max-w-2xl text-sm leading-7 text-[var(--muted-strong)]">
                The generator models internal ledger entries, processor
                transactions and bank settlements. A ground-truth manifest names
                each injected anomaly so tests can compare expected and actual
                outcomes.
              </p>
            </div>
          </CardContent>
        </Card>
        <Card className="col-span-12 bg-[var(--ink)] text-white lg:col-span-5">
          <CardContent className="flex min-h-80 flex-col justify-between">
            <p className="utility-label text-white/45">
              Reproducibility contract
            </p>
            <div className="mt-12 grid grid-cols-2 gap-3">
              <div className="rounded-2xl border border-white/10 bg-white/5 p-4">
                <Sprout size={22} aria-hidden="true" />
                <h2 className="mt-7 font-display text-3xl font-bold uppercase">
                  Fixed seeds
                </h2>
                <p className="mt-2 text-xs leading-5 text-white/55">
                  The same seed and scenario configuration produce the same rows
                  and checksums.
                </p>
              </div>
              <div className="rounded-2xl border border-white/10 bg-white/5 p-4">
                <FileCode size={22} aria-hidden="true" />
                <h2 className="mt-7 font-display text-3xl font-bold uppercase">
                  Manifests
                </h2>
                <p className="mt-2 text-xs leading-5 text-white/55">
                  Machine-readable ground truth records every injected
                  exception.
                </p>
              </div>
            </div>
          </CardContent>
        </Card>
      </div>

      <Card className="mb-3">
        <CardContent>
          <div className="mb-6">
            <p className="utility-label text-[var(--muted)]">
              Published scenarios
            </p>
            <h2 className="mt-2 font-display text-4xl font-bold uppercase">
              Different operational pressures
            </h2>
          </div>
          {scenarios.error ? (
            <ApiErrorState
              error={scenarios.error}
              onRetry={() => void scenarios.refetch()}
            />
          ) : (
            <div className="grid gap-3 md:grid-cols-2 xl:grid-cols-4">
              {(scenarios.data?.items ?? []).map((scenario, index) => (
                <div
                  key={scenario.slug}
                  className="relative overflow-hidden rounded-2xl border border-[var(--border)] bg-[var(--surface)] p-5"
                >
                  <span
                    className={`absolute inset-x-0 top-0 h-1.5 ${scenarioTones[index % scenarioTones.length]}`}
                    aria-hidden="true"
                  />
                  <p className="utility-label text-[var(--muted)]">
                    {scenario.slug}
                  </p>
                  <h3 className="mt-8 font-display text-3xl font-bold uppercase">
                    {scenario.name}
                  </h3>
                  <p className="mt-3 text-xs leading-5 text-[var(--muted-strong)]">
                    {scenario.purpose}
                  </p>
                  <div className="mt-5 flex justify-between border-t border-black/10 pt-3 text-xs">
                    <span>
                      {formatCount(scenario.canonicalPaymentCount)} payments
                    </span>
                    <span className="font-bold">
                      {formatCount(scenario.expectedExceptionCount)} expected
                      exceptions
                    </span>
                  </div>
                </div>
              ))}
              {scenarios.isPending ? (
                <p className="text-sm text-[var(--muted)]">
                  Loading scenario metadata…
                </p>
              ) : null}
            </div>
          )}
        </CardContent>
      </Card>

      <div className="grid gap-3 lg:grid-cols-2">
        <Card>
          <CardContent>
            <p className="utility-label text-[var(--muted)]">
              Ground-truth vocabulary
            </p>
            <h2 className="mt-2 font-display text-4xl font-bold uppercase">
              Exception definitions
            </h2>
            <dl className="mt-5 divide-y divide-[var(--border)]">
              {definitions.map(([term, description]) => (
                <div
                  key={term}
                  className="py-4 sm:grid sm:grid-cols-[.34fr_.66fr] sm:gap-5"
                >
                  <dt className="font-bold">{term}</dt>
                  <dd className="mt-1 text-sm leading-6 text-[var(--muted)] sm:mt-0">
                    {description}
                  </dd>
                </div>
              ))}
            </dl>
          </CardContent>
        </Card>
        <div className="grid gap-3">
          <Card className="overflow-hidden border-t-4 border-t-[var(--violet)]">
            <CardContent>
              <Database size={26} aria-hidden="true" />
              <h2 className="mt-8 font-display text-4xl font-bold uppercase">
                How generation works
              </h2>
              <ol className="mt-4 space-y-3 text-sm leading-6">
                <li>
                  <strong>1. Build canonical payments.</strong> Stable IDs,
                  currencies and event times begin from a seed.
                </li>
                <li>
                  <strong>2. Derive three sources.</strong> Ledger, processor
                  and bank rows preserve realistic reference differences.
                </li>
                <li>
                  <strong>3. Inject named anomalies.</strong> Scenario
                  configuration controls duplicates, fees, refunds and timing.
                </li>
                <li>
                  <strong>4. Verify invariants.</strong> Verification commands
                  check determinism, checksums and mathematical consistency.
                </li>
              </ol>
            </CardContent>
          </Card>
          <Card className="overflow-hidden border-t-4 border-t-[var(--coral)]">
            <CardContent>
              <h2 className="font-display text-3xl font-bold uppercase">
                Limits, stated plainly
              </h2>
              <p className="mt-3 text-sm leading-6 text-[var(--muted-strong)]">
                Synthetic distributions cannot reproduce every processor quirk,
                regional banking calendar or provider-specific data-quality
                pattern. They support repeatable reconciliation testing; they
                are not estimates of real-world throughput or financial
                outcomes.
              </p>
            </CardContent>
          </Card>
        </div>
      </div>

      <Card className="mt-3">
        <CardContent className="flex flex-col justify-between gap-5 sm:flex-row sm:items-center">
          <div>
            <p className="utility-label text-[var(--muted)]">
              Licence and source
            </p>
            <h2 className="mt-2 font-display text-4xl font-bold uppercase">
              Reuse with attribution
            </h2>
            <p className="mt-2 text-sm text-[var(--muted)]">
              Generated dataset: CC BY 4.0 · Generator source: MIT
            </p>
          </div>
          <div className="flex flex-wrap gap-2">
            <Button asChild variant="secondary">
              <a
                href="https://github.com/heybadrinath/reconriver"
                target="_blank"
                rel="noreferrer"
              >
                <FileCode2 size={18} aria-hidden="true" />
                View generator
              </a>
            </Button>
            <Button asChild>
              <a href={datasetUrl} target="_blank" rel="noreferrer">
                Download dataset <ArrowRight size={18} aria-hidden="true" />
              </a>
            </Button>
          </div>
        </CardContent>
      </Card>
    </AppShell>
  );
}
