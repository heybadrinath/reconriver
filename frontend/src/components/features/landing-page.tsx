"use client";

import {
  ArrowRight,
  Braces,
  CircleCheck,
  Database,
  Download,
  FileCode2,
  GitBranch,
  Hourglass,
  ListChecks,
  KeyRound,
  ShieldAlert,
  Layers3,
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

const capabilities = [
  {
    icon: ListChecks,
    title: "Deterministic matching",
    text: "Every result names the rule that matched, rejected or routed a record to investigation.",
    tone: "bg-[var(--coral-soft)]",
  },
  {
    icon: GitBranch,
    title: "Restartable processing",
    text: "Spring Batch checkpoints work so an interrupted run can resume without duplicating results.",
    tone: "bg-[var(--yellow-soft)]",
  },
  {
    icon: KeyRound,
    title: "Isolated public demo",
    text: "Short-lived workspaces keep synthetic uploads separated and automatically expire after 24 hours.",
    tone: "bg-[var(--green-soft)]",
  },
];

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
      "The processor fee differs from the configured policy, so the flow enters investigation.",
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

  function tryWorkspace() {
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
      <div className="mx-auto max-w-[1540px] overflow-hidden bg-[var(--canvas)] sm:rounded-[2rem] sm:border sm:border-[var(--border-strong)] sm:shadow-[var(--shadow-shell)]">
        <header className="flex items-center justify-between gap-4 px-4 py-4 sm:px-7 xl:px-10">
          <Logo />
          <nav
            className="hidden items-center gap-1 md:flex"
            aria-label="Primary navigation"
          >
            {[
              { href: "#how-it-works", label: "How it works" },
              { href: "#architecture", label: "Architecture" },
              { href: "/dataset", label: "Dataset" },
            ].map(({ href, label }) => (
              <Link
                key={href}
                href={href}
                className="rounded-full px-4 py-2 text-sm font-bold text-[var(--muted-strong)] hover:bg-white focus-visible:ring-3 focus-visible:ring-[var(--focus)] focus-visible:outline-none"
              >
                {label}
              </Link>
            ))}
          </nav>
          <Button
            variant="primary"
            onClick={tryWorkspace}
            disabled={workspace.start.isPending}
          >
            {workspace.start.isPending ? (
              <Hourglass
                className="animate-pulse"
                size={18}

                aria-hidden="true"
              />
            ) : (
              <ArrowRight size={18} aria-hidden="true" />
            )}
            <span className="hidden sm:inline">
              {workspace.hasWorkspace ? "Open workspace" : "Try ReconRiver"}
            </span>
            <span className="sm:hidden">
              {workspace.hasWorkspace ? "Open" : "Try demo"}
            </span>
          </Button>
        </header>

        <main id="main-content">
          <section className="grid gap-3 px-4 pb-3 sm:px-6 xl:grid-cols-[minmax(0,1.08fr)_minmax(400px,.92fr)] xl:px-8">
            <div className="flex min-h-[590px] flex-col justify-between rounded-[1.75rem] border border-[var(--border)] bg-white p-6 sm:p-9 xl:p-12">
              <div>
                <p className="utility-label mb-6 flex items-center gap-2 text-[var(--muted)]">
                  <span className="size-2 rounded-full bg-[var(--green)]" />
                  Payment Reconciliation &amp; Settlement Workbench
                </p>
                <h1 className="max-w-[860px] font-display text-[clamp(4rem,10vw,9.5rem)] leading-[0.78] font-extrabold tracking-[-0.045em] uppercase">
                  Trace every payment
                  <span className="block text-[var(--coral-strong)]">
                    to settlement.
                  </span>
                </h1>
                <p className="mt-7 max-w-2xl text-base leading-7 text-[var(--muted-strong)] sm:text-lg sm:leading-8">
                  Compare internal payments, processor transactions and bank
                  settlements. Detect duplicates, incorrect fees, refunds and
                  missing deposits with an explanation attached to every result.
                </p>
              </div>
              <div className="mt-10 flex flex-col gap-4 sm:flex-row sm:items-center">
                <Button
                  size="lg"
                  variant="coral"
                  onClick={tryWorkspace}
                  disabled={workspace.start.isPending}
                >
                  Start with synthetic data
                  <ArrowRight size={19} aria-hidden="true" />
                </Button>
                <p className="max-w-xs text-xs leading-5 text-[var(--muted)]">
                  No registration. Your isolated demo workspace expires
                  automatically after 24 hours.
                </p>
              </div>
            </div>

            <div className="flex min-h-[590px] flex-col rounded-[1.75rem] border border-black bg-[var(--ink)] p-5 text-white sm:p-7">
              <div className="flex items-start justify-between gap-4">
                <div>
                  <p className="font-mono text-[0.65rem] tracking-[0.14em] text-white/55 uppercase">
                    Conceptual preview
                  </p>
                  <h2 className="mt-2 font-display text-4xl leading-none font-bold uppercase">
                    Follow the rule, not a black box
                  </h2>
                </div>
                <span className="grid size-11 shrink-0 place-items-center rounded-full bg-white text-[var(--ink)]">
                  <GitBranch size={22} aria-hidden="true" />
                </span>
              </div>

              <div className="my-auto py-8">
                <div
                  className="grid grid-cols-[auto_1fr_auto_1fr_auto_1fr_auto] items-center gap-2"
                  aria-label="Internal ledger to bank flow"
                >
                  {["Ledger", "Processor", "Batch", "Bank"].map(
                    (stage, index) => (
                      <div key={stage} className="contents">
                        <span className="grid aspect-square min-w-14 place-items-center rounded-2xl border border-white/15 bg-white/6 px-2 text-center font-display text-lg font-bold uppercase sm:min-w-20">
                          {stage}
                        </span>
                        {index < 3 ? (
                          <span
                            className="h-1 rounded-full"
                            style={{ backgroundColor: active?.tone }}
                            aria-hidden="true"
                          />
                        ) : null}
                      </div>
                    ),
                  )}
                </div>
                <div
                  className="mt-7 rounded-2xl border border-white/10 bg-white/5 p-4"
                  aria-live="polite"
                >
                  <p className="font-mono text-[0.65rem] tracking-[0.12em] text-white/55 uppercase">
                    Why this outcome
                  </p>
                  <p className="mt-2 text-sm leading-6 text-white/85">
                    {active?.detail}
                  </p>
                </div>
              </div>

              <div
                className="grid grid-cols-2 gap-2"
                role="group"
                aria-label="Preview a reconciliation rule"
              >
                {previewRules.map((rule) => (
                  <button
                    key={rule.id}
                    type="button"
                    onClick={() => setActiveRule(rule.id)}
                    className={cn(
                      "flex min-h-16 items-center gap-3 rounded-2xl border p-3 text-left text-xs font-bold focus-visible:ring-3 focus-visible:ring-[var(--focus)] focus-visible:outline-none",
                      activeRule === rule.id
                        ? "border-white bg-white text-[var(--ink)]"
                        : "border-white/12 bg-transparent text-white hover:bg-white/6",
                    )}
                    aria-pressed={activeRule === rule.id}
                  >
                    <span
                      className="size-3 shrink-0 rounded-full"
                      style={{ backgroundColor: rule.tone }}
                    />
                    {rule.label}
                  </button>
                ))}
              </div>
            </div>
          </section>

          {workspace.start.isPending && showColdStart ? (
            <div className="mx-4 my-3 sm:mx-6 xl:mx-8">
              <ColdStartState />
            </div>
          ) : null}
          {workspace.start.error ? (
            <div className="mx-4 my-3 sm:mx-6 xl:mx-8">
              <ApiErrorState
                error={workspace.start.error}
                onRetry={tryWorkspace}
              />
            </div>
          ) : null}

          <section
            id="how-it-works"
            className="px-4 py-16 sm:px-6 sm:py-24 xl:px-8"
          >
            <div className="mb-8 flex flex-col justify-between gap-4 md:flex-row md:items-end">
              <div>
                <p className="utility-label text-[var(--muted)]">
                  Built for investigation
                </p>
                <h2 className="mt-2 max-w-3xl font-display text-[clamp(3.3rem,7vw,7.5rem)] leading-[0.82] font-extrabold tracking-[-0.035em] uppercase">
                  Spreadsheet work,
                  <br />
                  made repeatable.
                </h2>
              </div>
              <p className="max-w-md text-sm leading-6 text-[var(--muted-strong)]">
                Import three source files, run a checkpointed job, inspect
                exceptions and preserve every operator decision in an
                append-only audit trail.
              </p>
            </div>
            <div className="grid gap-3 lg:grid-cols-3">
              {capabilities.map(({ icon: Icon, title, text, tone }) => (
                <Card key={title} className={cn("min-h-72", tone)}>
                  <CardContent className="flex h-full flex-col justify-between">
                    <span className="grid size-12 place-items-center rounded-full bg-white">
                      <Icon size={24} aria-hidden="true" />
                    </span>
                    <div className="mt-14">
                      <h3 className="font-display text-3xl leading-none font-bold uppercase">
                        {title}
                      </h3>
                      <p className="mt-3 text-sm leading-6 text-[var(--muted-strong)]">
                        {text}
                      </p>
                    </div>
                  </CardContent>
                </Card>
              ))}
            </div>
          </section>

          <section
            id="architecture"
            className="grid gap-3 px-4 pb-3 sm:px-6 xl:grid-cols-[.8fr_1.2fr] xl:px-8"
          >
            <div className="rounded-[1.75rem] border border-black bg-[var(--ink)] p-6 text-white sm:p-9">
              <p className="utility-label text-white/50">Java at the center</p>
              <h2 className="mt-4 font-display text-[clamp(3.5rem,7vw,7rem)] leading-[0.82] font-extrabold uppercase">
                Business rules live where they belong.
              </h2>
              <p className="mt-6 max-w-lg text-sm leading-7 text-white/70">
                The Next.js interface presents facts. Spring Boot owns
                validation, BigDecimal calculations, matching, transaction
                boundaries, state transitions and audit events.
              </p>
              <Button asChild variant="secondary" className="mt-8">
                <Link href="/architecture">
                  Explore the architecture
                  <ArrowRight size={18} aria-hidden="true" />
                </Link>
              </Button>
            </div>
            <div className="grid gap-3 sm:grid-cols-2">
              {[
                {
                  icon: Braces,
                  title: "Spring Boot API",
                  text: "Versioned REST endpoints, Problem Details and generated OpenAPI types.",
                },
                {
                  icon: Layers3,
                  title: "Modular monolith",
                  text: "Explicit module boundaries without distributed-system overhead.",
                },
                {
                  icon: Database,
                  title: "PostgreSQL + Flyway",
                  text: "Constraints, locks and repeatable schema migrations protect invariants.",
                },
                {
                  icon: CircleCheck,
                  title: "Testcontainers",
                  text: "The integration-test design targets the same PostgreSQL behavior the app depends on.",
                },
              ].map(({ icon: Icon, title, text }, index) => (
                <Card
                  key={title}
                  className={
                    index === 1
                      ? "bg-[var(--violet-soft)]"
                      : index === 2
                        ? "bg-[var(--yellow-soft)]"
                        : ""
                  }
                >
                  <CardContent>
                    <Icon size={26} aria-hidden="true" />
                    <h3 className="mt-12 font-display text-3xl font-bold uppercase">
                      {title}
                    </h3>
                    <p className="mt-2 text-sm leading-6 text-[var(--muted)]">
                      {text}
                    </p>
                  </CardContent>
                </Card>
              ))}
            </div>
          </section>

          <section className="grid gap-3 px-4 py-3 sm:px-6 lg:grid-cols-[1.1fr_.9fr] xl:px-8">
            <Card className="bg-[var(--yellow-soft)]">
              <CardContent className="flex h-full flex-col justify-between gap-10 sm:flex-row sm:items-end">
                <div>
                  <p className="utility-label text-[#6d5310]">
                    Public synthetic dataset
                  </p>
                  <h2 className="mt-3 max-w-2xl font-display text-5xl leading-[0.86] font-extrabold uppercase sm:text-7xl">
                    Realistic edge cases, no private payment data.
                  </h2>
                  <p className="mt-5 max-w-2xl text-sm leading-6 text-[var(--muted-strong)]">
                    A deterministic Java generator creates clean settlements,
                    mixed exceptions, month-end volume and failure-recovery
                    scenarios with ground-truth manifests.
                  </p>
                </div>
                <Button asChild variant="primary" className="shrink-0">
                  <Link href="/dataset">
                    View dataset
                    <Download
                      size={18}

                      aria-hidden="true"
                    />
                  </Link>
                </Button>
              </CardContent>
            </Card>
            <Card className="bg-[var(--coral-soft)]">
              <CardContent className="flex h-full flex-col justify-between gap-8">
                <ShieldAlert size={34} aria-hidden="true" />
                <div>
                  <h2 className="font-display text-4xl font-bold uppercase">
                    Synthetic data only
                  </h2>
                  <p className="mt-3 text-sm leading-6 text-[var(--muted-strong)]">
                    Do not upload real payment, customer, card or bank
                    information. This portfolio demo is not production financial
                    infrastructure.
                  </p>
                </div>
              </CardContent>
            </Card>
          </section>
        </main>

        <footer className="mx-4 mt-16 border-t border-[var(--border-strong)] px-1 py-8 sm:mx-6 xl:mx-8">
          <div className="flex flex-col justify-between gap-7 lg:flex-row lg:items-center">
            <div>
              <Logo />
              <p className="mt-3 text-xs text-[var(--muted)]">
                MIT application code · CC BY 4.0 generated dataset
              </p>
            </div>
            <nav
              className="flex flex-wrap gap-x-6 gap-y-3 text-sm font-bold"
              aria-label="Project links"
            >
              <a
                href="https://github.com/heybadrinath/reconriver"
                target="_blank"
                rel="noreferrer"
                className="inline-flex items-center gap-2 hover:underline"
              >
                <FileCode2 size={18} aria-hidden="true" /> GitHub
              </a>
              <a
                href={
                  process.env.NEXT_PUBLIC_DATASET_URL ??
                  "https://huggingface.co/datasets"
                }
                target="_blank"
                rel="noreferrer"
                className="hover:underline"
              >
                Dataset
              </a>
              <a
                href={`${process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080"}/docs`}
                target="_blank"
                rel="noreferrer"
                className="hover:underline"
              >
                API documentation
              </a>
              <a
                href="https://ram-badrinath.vercel.app/"
                target="_blank"
                rel="noreferrer"
                className="hover:underline"
              >
                Portfolio
              </a>
            </nav>
          </div>
        </footer>
      </div>
    </div>
  );
}
