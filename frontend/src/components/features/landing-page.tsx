"use client";

import {
  ArrowRight,
  BookOpen,
  CircleCheck,
  CreditCard,
  FileCode2,
  GitBranch,
  Hourglass,
  Landmark,
  ListChecks,
  Play,
  ShieldAlert,
  Upload,
} from "lucide-react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";

import { Logo } from "@/components/layout/logo";
import { ApiErrorState, ColdStartState } from "@/components/states/api-state";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { useWorkspace } from "@/lib/hooks/use-workspace";
import { cn } from "@/lib/utils";

const previewRules = [
  {
    id: "reference",
    label: "Reference match",
    tone: "var(--green)",
    detail:
      "Merchant order ID and currency agree across the internal and processor records.",
  },
  {
    id: "fee",
    label: "Fee discrepancy",
    tone: "var(--coral)",
    detail:
      "The processor fee differs from the configured policy, so the result is routed for review.",
  },
  {
    id: "late",
    label: "Late settlement",
    tone: "var(--yellow)",
    detail: "The bank credit arrived outside the configured settlement window.",
  },
  {
    id: "refund",
    label: "Refund branch",
    tone: "var(--violet)",
    detail:
      "A refund links back to the original payment while preserving its own settlement path.",
  },
];

const sources = [
  {
    icon: BookOpen,
    title: "Internal ledger",
    detail: "Orders, amounts and internal references",
  },
  {
    icon: CreditCard,
    title: "Processor",
    detail: "Events, fees, refunds and batches",
  },
  {
    icon: Landmark,
    title: "Bank settlement",
    detail: "Deposits, currencies and booking dates",
  },
];

const workflow = [
  {
    icon: Upload,
    step: "01",
    title: "Load sources",
    detail: "Bring the ledger, processor and bank files together.",
  },
  {
    icon: Play,
    step: "02",
    title: "Run reconciliation",
    detail: "Apply ordered rules with checkpointed processing.",
  },
  {
    icon: ListChecks,
    step: "03",
    title: "Resolve exceptions",
    detail: "Compare evidence and record an auditable decision.",
  },
];

const matchedResults = 1_007;
const exceptionResults = 237;
const totalResults = 1_244;

export function LandingPage() {
  const router = useRouter();
  const workspace = useWorkspace();
  const [activeRule, setActiveRule] = useState(
    previewRules[0]?.id ?? "reference",
  );
  const [showColdStart, setShowColdStart] = useState(false);
  const active =
    previewRules.find((rule) => rule.id === activeRule) ?? previewRules[0];

  useEffect(() => {
    if (!workspace.start.isPending) return;
    const timer = window.setTimeout(() => setShowColdStart(true), 3_500);
    return () => window.clearTimeout(timer);
  }, [workspace.start.isPending]);

  function openWorkspace() {
    if (workspace.hasWorkspace) {
      router.push("/scenarios");
      return;
    }
    setShowColdStart(false);
    workspace.start.mutate(undefined, {
      onSuccess: () => router.push("/scenarios"),
    });
  }

  return (
    <div className="min-h-screen bg-[var(--environment)] p-0 sm:p-3 xl:p-6">
      <div className="mx-auto max-w-[1440px] overflow-hidden bg-[var(--canvas)] sm:rounded-[2rem] sm:border sm:border-[var(--border-strong)] sm:shadow-[var(--shadow-shell)]">
        <header className="flex min-h-20 items-center justify-between gap-4 px-4 py-3 sm:px-7 xl:px-8">
          <Logo />
          <nav
            className="hidden items-center gap-1 rounded-full border border-[var(--border)] bg-white/70 p-1 md:flex"
            aria-label="Primary navigation"
          >
            <Link
              href="#product"
              className="inline-flex min-h-11 items-center rounded-full bg-[var(--ink)] px-5 text-sm font-bold text-white focus-visible:ring-3 focus-visible:ring-[var(--focus)] focus-visible:outline-none"
            >
              Product
            </Link>
            <Link
              href="#workflow"
              className="inline-flex min-h-11 items-center rounded-full px-5 text-sm font-bold text-[var(--muted-strong)] hover:bg-white focus-visible:ring-3 focus-visible:ring-[var(--focus)] focus-visible:outline-none"
            >
              Workflow
            </Link>
            <Link
              href="/dataset"
              className="inline-flex min-h-11 items-center rounded-full px-5 text-sm font-bold text-[var(--muted-strong)] hover:bg-white focus-visible:ring-3 focus-visible:ring-[var(--focus)] focus-visible:outline-none"
            >
              Data
            </Link>
            <Link
              href="/architecture"
              className="inline-flex min-h-11 items-center rounded-full px-5 text-sm font-bold text-[var(--muted-strong)] hover:bg-white focus-visible:ring-3 focus-visible:ring-[var(--focus)] focus-visible:outline-none"
            >
              Architecture
            </Link>
          </nav>
          <Button
            variant="primary"
            onClick={openWorkspace}
            disabled={workspace.start.isPending}
          >
            {workspace.start.isPending ? (
              <Hourglass size={18} aria-hidden="true" />
            ) : (
              <ArrowRight size={18} aria-hidden="true" />
            )}
            <span className="hidden sm:inline">
              {workspace.hasWorkspace ? "Open workspace" : "Start workspace"}
            </span>
            <span className="sm:hidden">
              {workspace.hasWorkspace ? "Open" : "Start"}
            </span>
          </Button>
        </header>

        <main id="main-content">
          <section
            id="product"
            className="grid gap-4 px-4 py-5 sm:px-6 sm:py-6 lg:grid-cols-[minmax(0,1fr)_minmax(320px,.48fr)] lg:items-end xl:px-8"
          >
            <div>
              <p className="utility-label flex items-center gap-2 text-[var(--muted)]">
                <span className="size-2 rounded-full bg-[var(--green)]" />
                Payment reconciliation
              </p>
              <h1 className="mt-3 max-w-5xl font-display text-[clamp(3.1rem,4.4vw,4.3rem)] leading-[0.84] font-extrabold tracking-[-0.04em] uppercase">
                Close settlements
                <span className="block text-[var(--coral-strong)]">
                  with every variance explained.
                </span>
              </h1>
            </div>
            <div className="lg:pb-1">
              <p className="max-w-xl text-sm leading-6 text-[var(--muted-strong)] sm:text-base">
                Connect internal payments, processor activity and bank deposits.
                See what matched, what needs attention and the exact rule behind
                every result.
              </p>
              <div className="mt-4 flex flex-col gap-2 sm:flex-row">
                <Button
                  size="lg"
                  variant="coral"
                  onClick={openWorkspace}
                  disabled={workspace.start.isPending}
                >
                  {workspace.start.isPending ? "Starting…" : "Start workspace"}
                  <ArrowRight size={19} aria-hidden="true" />
                </Button>
                <Button asChild size="lg" variant="secondary">
                  <Link href="#workflow">See the workflow</Link>
                </Button>
              </div>
              <p className="mt-2 text-xs leading-5 text-[var(--muted)]">
                No registration. Workspaces expire automatically after 24 hours.
              </p>
            </div>
          </section>

          {workspace.start.isPending && showColdStart ? (
            <div className="mx-4 mb-3 sm:mx-6 xl:mx-8">
              <ColdStartState />
            </div>
          ) : null}
          {workspace.start.error ? (
            <div className="mx-4 mb-3 sm:mx-6 xl:mx-8">
              <ApiErrorState
                error={workspace.start.error}
                onRetry={openWorkspace}
              />
            </div>
          ) : null}

          <section className="grid gap-3 px-4 pb-3 sm:px-6 lg:grid-cols-12 xl:px-8">
            <Card className="min-h-[390px] lg:col-span-7">
              <CardContent className="flex h-full min-h-[390px] flex-col justify-between">
                <div className="flex items-start justify-between gap-4">
                  <div>
                    <p className="utility-label text-[var(--muted)]">
                      Deterministic sample · Mixed exceptions
                    </p>
                    <h2 className="mt-2 font-display text-4xl leading-none font-bold uppercase sm:text-5xl">
                      Reconciliation summary
                    </h2>
                  </div>
                  <span className="grid size-11 shrink-0 place-items-center rounded-full border border-[var(--border)] bg-[var(--surface-muted)]">
                    <CircleCheck size={22} aria-hidden="true" />
                  </span>
                </div>

                <div className="mt-10">
                  <p className="metric-number">1,244</p>
                  <p className="mt-2 text-sm text-[var(--muted)]">
                    reconciliation results
                  </p>
                  <div
                    className="mt-8 flex h-5 overflow-hidden rounded-full bg-[var(--surface-muted)] p-1"
                    role="img"
                    aria-label="1,007 matched results and 237 exceptions out of 1,244 total results"
                  >
                    <span
                      className="h-full rounded-full bg-[var(--green)]"
                      style={{ flexGrow: matchedResults }}
                      aria-hidden="true"
                    />
                    <span
                      className="ml-1 h-full rounded-full bg-[var(--coral)]"
                      style={{ flexGrow: exceptionResults }}
                      aria-hidden="true"
                    />
                  </div>
                  <dl className="mt-5 grid grid-cols-1 gap-3 sm:grid-cols-3">
                    <div className="rounded-2xl border border-[var(--border)] bg-[var(--surface-muted)] p-4">
                      <dt className="utility-label text-[var(--muted)]">
                        Total
                      </dt>
                      <dd className="mt-3 font-display text-4xl font-bold">
                        {totalResults.toLocaleString("en-US")}
                      </dd>
                    </div>
                    <div className="rounded-2xl border border-[#80bda2] bg-[var(--green-soft)] p-4">
                      <dt className="utility-label text-[#165f44]">Matched</dt>
                      <dd className="mt-3 font-display text-4xl font-bold">
                        {matchedResults.toLocaleString("en-US")}
                      </dd>
                    </div>
                    <div className="rounded-2xl border border-[#e5a89d] bg-[var(--coral-soft)] p-4">
                      <dt className="utility-label text-[#8e291d]">
                        Exceptions
                      </dt>
                      <dd className="mt-3 font-display text-4xl font-bold">
                        {exceptionResults.toLocaleString("en-US")}
                      </dd>
                    </div>
                  </dl>
                </div>
              </CardContent>
            </Card>

            <Card className="min-h-[390px] border-black bg-[var(--ink)] text-white lg:col-span-5">
              <CardContent className="flex h-full min-h-[390px] flex-col">
                <div className="flex items-start justify-between gap-4">
                  <div>
                    <p className="utility-label text-white/50">
                      Decision evidence
                    </p>
                    <h2 className="mt-2 max-w-lg font-display text-4xl leading-[0.9] font-bold uppercase sm:text-5xl">
                      Every result tells you why.
                    </h2>
                  </div>
                  <span className="grid size-11 shrink-0 place-items-center rounded-full bg-white text-[var(--ink)]">
                    <GitBranch size={22} aria-hidden="true" />
                  </span>
                </div>

                <div
                  className="my-7 rounded-2xl border border-white/10 bg-white/5 p-4"
                  aria-live="polite"
                >
                  <p className="font-mono text-[0.65rem] tracking-[0.12em] text-white/50 uppercase">
                    Why this outcome
                  </p>
                  <p className="mt-3 text-sm leading-6 text-white/80">
                    {active?.detail}
                  </p>
                </div>

                <div
                  className="mt-auto grid grid-cols-1 gap-2 sm:grid-cols-2"
                  role="group"
                  aria-label="Preview a reconciliation rule"
                >
                  {previewRules.map((rule) => (
                    <button
                      key={rule.id}
                      type="button"
                      onClick={() => setActiveRule(rule.id)}
                      className={cn(
                        "flex min-h-14 items-center gap-3 rounded-2xl border px-3 py-2 text-left text-xs font-bold focus-visible:ring-3 focus-visible:ring-[var(--focus)] focus-visible:outline-none",
                        activeRule === rule.id
                          ? "border-white bg-white text-[var(--ink)]"
                          : "border-white/12 text-white hover:bg-white/6",
                      )}
                      aria-pressed={activeRule === rule.id}
                    >
                      <span
                        className="size-3 shrink-0 rounded-full"
                        style={{ backgroundColor: rule.tone }}
                        aria-hidden="true"
                      />
                      {rule.label}
                    </button>
                  ))}
                </div>
              </CardContent>
            </Card>
          </section>

          <section
            id="workflow"
            className="grid gap-3 px-4 pb-3 sm:px-6 lg:grid-cols-12 xl:px-8"
          >
            <Card className="lg:col-span-7">
              <CardContent>
                <div className="flex items-start justify-between gap-4">
                  <div>
                    <p className="utility-label text-[var(--muted)]">
                      Source coverage
                    </p>
                    <h2 className="mt-2 font-display text-4xl font-bold uppercase">
                      One view across every settlement stage.
                    </h2>
                  </div>
                  <span className="hidden rounded-full bg-[var(--green-soft)] px-3 py-2 text-xs font-bold text-[#165f44] sm:inline-flex">
                    Three inputs
                  </span>
                </div>
                <div className="mt-7 grid gap-3 sm:grid-cols-3">
                  {sources.map(({ icon: Icon, title, detail }) => (
                    <div
                      key={title}
                      className="rounded-[1.25rem] border border-[var(--border)] bg-[var(--surface-muted)] p-4"
                    >
                      <span className="grid size-10 place-items-center rounded-full bg-white">
                        <Icon size={20} aria-hidden="true" />
                      </span>
                      <h3 className="mt-7 font-display text-2xl leading-none font-bold uppercase">
                        {title}
                      </h3>
                      <p className="mt-2 text-xs leading-5 text-[var(--muted)]">
                        {detail}
                      </p>
                    </div>
                  ))}
                </div>
              </CardContent>
            </Card>

            <Card className="bg-[var(--yellow-soft)] lg:col-span-5">
              <CardContent className="flex h-full flex-col">
                <p className="utility-label text-[#69500b]">
                  Reconciliation workflow
                </p>
                <h2 className="mt-2 font-display text-4xl font-bold uppercase">
                  From source files to an auditable decision.
                </h2>
                <ol className="mt-6 space-y-2">
                  {workflow.map(({ icon: Icon, step, title, detail }) => (
                    <li
                      key={step}
                      className="grid grid-cols-[auto_1fr] gap-3 rounded-2xl border border-black/10 bg-white/60 p-3"
                    >
                      <span className="grid size-10 place-items-center rounded-full bg-[var(--ink)] text-white">
                        <Icon size={18} aria-hidden="true" />
                      </span>
                      <div>
                        <p className="font-mono text-[0.62rem] font-semibold tracking-[0.12em] text-[var(--muted)] uppercase">
                          Step {step}
                        </p>
                        <h3 className="font-bold">{title}</h3>
                        <p className="mt-1 text-xs leading-5 text-[var(--muted-strong)]">
                          {detail}
                        </p>
                      </div>
                    </li>
                  ))}
                </ol>
                <Button
                  className="mt-5 self-start"
                  onClick={openWorkspace}
                  disabled={workspace.start.isPending}
                >
                  Start workspace <ArrowRight size={18} aria-hidden="true" />
                </Button>
              </CardContent>
            </Card>
          </section>

          <section className="mx-4 mt-3 flex flex-col justify-between gap-5 rounded-[var(--radius-card)] border border-[#e5a89d] bg-[var(--coral-soft)] p-5 sm:mx-6 sm:flex-row sm:items-center sm:p-6 xl:mx-8">
            <div className="flex items-start gap-3">
              <span className="grid size-11 shrink-0 place-items-center rounded-full bg-white">
                <ShieldAlert size={22} aria-hidden="true" />
              </span>
              <div>
                <h2 className="font-display text-3xl leading-none font-bold uppercase">
                  Synthetic data boundary
                </h2>
                <p className="mt-2 max-w-3xl text-sm leading-6 text-[var(--muted-strong)]">
                  This workspace accepts synthetic payment data only. Do not
                  upload real customer, card or bank information.
                </p>
              </div>
            </div>
            <Button asChild variant="secondary" className="shrink-0">
              <Link href="/dataset">View sample data</Link>
            </Button>
          </section>
        </main>

        <footer className="mx-4 mt-16 border-t border-[var(--border-strong)] px-1 py-8 sm:mx-6 xl:mx-8">
          <div className="flex flex-col justify-between gap-7 lg:flex-row lg:items-center">
            <div>
              <Logo />
              <p className="mt-3 max-w-md text-xs leading-5 text-[var(--muted)]">
                Explainable payment reconciliation from source record to final
                settlement.
              </p>
            </div>
            <nav
              className="flex flex-wrap gap-x-6 gap-y-3 text-sm font-bold"
              aria-label="Product links"
            >
              <Link
                href="#product"
                className="inline-flex min-h-11 items-center hover:underline"
              >
                Product
              </Link>
              <a
                href={
                  process.env.NEXT_PUBLIC_DATASET_URL ??
                  "https://huggingface.co/datasets"
                }
                target="_blank"
                rel="noreferrer"
                className="inline-flex min-h-11 items-center hover:underline"
              >
                Dataset
              </a>
              <a
                href={`${process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080"}/docs`}
                target="_blank"
                rel="noreferrer"
                className="inline-flex min-h-11 items-center hover:underline"
              >
                API documentation
              </a>
              <a
                href="https://github.com/heybadrinath/reconriver"
                target="_blank"
                rel="noreferrer"
                className="inline-flex min-h-11 items-center gap-2 hover:underline"
              >
                <FileCode2 size={18} aria-hidden="true" /> GitHub
              </a>
            </nav>
          </div>
        </footer>
      </div>
    </div>
  );
}
