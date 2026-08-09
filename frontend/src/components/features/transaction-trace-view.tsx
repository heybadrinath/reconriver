"use client";

import { useQuery } from "@tanstack/react-query";
import {
  ArrowDown,
  Landmark,
  Building2,
  CreditCard,
  GitBranch,
  Receipt,
  RotateCcw,
  LoaderCircle,
} from "lucide-react";
import Link from "next/link";

import { PageIntro } from "@/components/layout/page-intro";
import { ApiErrorState } from "@/components/states/api-state";
import { StatusBadge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { api } from "@/lib/api/client";
import type { TraceNode } from "@/lib/api/types";
import { formatDate, formatMoney, titleCase } from "@/lib/utils";

function displayValue(value: unknown) {
  if (value == null) return "Not recorded";
  if (typeof value === "object") return JSON.stringify(value);
  return String(value);
}

function TraceNodeCard({
  title,
  node,
  icon: Icon,
  tone,
}: {
  title: string;
  node: TraceNode | null;
  icon: typeof Building2;
  tone: string;
}) {
  const entries = Object.entries(node ?? {})
    .filter(([, value]) => value != null)
    .slice(0, 8);
  return (
    <Card className={tone}>
      <CardContent>
        <div className="flex items-center justify-between gap-3">
          <div className="flex items-center gap-2">
            <Icon size={21} aria-hidden="true" />
            <h2 className="font-display text-3xl font-bold uppercase">
              {title}
            </h2>
          </div>
          <StatusBadge value={node ? "PRESENT" : "MISSING"} />
        </div>
        {node ? (
          <dl className="mt-6 divide-y divide-black/8">
            {entries.map(([key, value]) => (
              <div
                key={key}
                className="grid grid-cols-[minmax(90px,.35fr)_minmax(0,.65fr)] gap-3 py-2.5"
              >
                <dt className="text-xs font-bold text-[var(--muted)]">
                  {titleCase(key)}
                </dt>
                <dd className="text-right font-mono text-[0.68rem] break-all">
                  {displayValue(value)}
                </dd>
              </div>
            ))}
          </dl>
        ) : (
          <p className="mt-12 text-sm leading-6 text-[var(--muted-strong)]">
            No record from this stage was linked to the transaction.
          </p>
        )}
      </CardContent>
    </Card>
  );
}

export function TransactionTraceView({
  transactionId,
}: {
  transactionId: string;
}) {
  const trace = useQuery({
    queryKey: ["transaction-trace", transactionId],
    queryFn: () => api.trace(transactionId),
  });
  if (trace.isPending)
    return (
      <div className="grid min-h-[55vh] place-items-center" role="status">
        <LoaderCircle className="animate-spin" size={30} aria-hidden="true" />
        <span className="sr-only">Loading transaction trace</span>
      </div>
    );
  if (trace.error)
    return (
      <ApiErrorState error={trace.error} onRetry={() => void trace.refetch()} />
    );
  const data = trace.data;
  const refundDecision = data.decisions.find((decision) =>
    `${decision.outcome} ${decision.ruleCode}`.toUpperCase().includes("REFUND"),
  );

  return (
    <>
      <PageIntro
        eyebrow={`Transaction · ${data.transactionId}`}
        title="Transaction trace"
        description="The complete evidence chain from the internal ledger through processor and settlement batch to the bank, with every matching decision and audit event preserved."
        actions={
          <Button asChild variant="secondary">
            <Link href="/exceptions">Back to exceptions</Link>
          </Button>
        }
      />

      <div className="grid gap-2 lg:grid-cols-[1fr_auto_1fr_auto_1fr_auto_1fr] lg:items-stretch">
        <TraceNodeCard
          title="Internal record"
          node={data.internal}
          icon={Building2}
          tone="bg-[var(--green-soft)]"
        />
        <div className="grid place-items-center text-[var(--muted)]">
          <ArrowDown className="lg:hidden" size={20} aria-hidden="true" />
          <GitBranch className="hidden lg:block" size={22} aria-hidden="true" />
        </div>
        <TraceNodeCard
          title="Processor record"
          node={data.processor}
          icon={CreditCard}
          tone="bg-[var(--surface)]"
        />
        <div className="grid place-items-center text-[var(--muted)]">
          <ArrowDown className="lg:hidden" size={20} aria-hidden="true" />
          <GitBranch className="hidden lg:block" size={22} aria-hidden="true" />
        </div>
        <TraceNodeCard
          title="Settlement batch"
          node={data.settlementBatch}
          icon={Receipt}
          tone="bg-[var(--yellow-soft)]"
        />
        <div className="grid place-items-center text-[var(--muted)]">
          <ArrowDown className="lg:hidden" size={20} aria-hidden="true" />
          <GitBranch className="hidden lg:block" size={22} aria-hidden="true" />
        </div>
        <TraceNodeCard
          title="Bank entry"
          node={data.bank}
          icon={Landmark}
          tone="bg-[var(--violet-soft)]"
        />
      </div>

      {data.refund ? (
        <div className="mt-3 max-w-xl">
          <TraceNodeCard
            title="Refund processor event"
            node={data.refund}
            icon={RotateCcw}
            tone="border-[#a98ade] bg-[var(--violet-soft)]"
          />
        </div>
      ) : null}

      {refundDecision ? (
        <Card className="mt-3 border-[#a98ade] bg-[var(--violet-soft)]">
          <CardContent className="flex flex-col justify-between gap-5 sm:flex-row sm:items-center">
            <div>
              <p className="utility-label text-[#6548a4]">Refund branch</p>
              <h2 className="mt-2 font-display text-4xl font-bold uppercase">
                Linked refund decision
              </h2>
              <p className="mt-2 max-w-3xl text-sm leading-6 text-[var(--muted-strong)]">
                {refundDecision.explanation}
              </p>
            </div>
            <div className="shrink-0 text-right">
              <p className="font-display text-4xl font-bold">
                {formatMoney(
                  refundDecision.grossAmount,
                  refundDecision.currency,
                )}
              </p>
              <StatusBadge value={refundDecision.outcome} />
            </div>
          </CardContent>
        </Card>
      ) : null}

      <div className="mt-3 grid gap-3 lg:grid-cols-2">
        <Card>
          <CardContent>
            <p className="utility-label text-[var(--muted)]">
              Rule engine output
            </p>
            <h2 className="mt-2 font-display text-4xl font-bold uppercase">
              Matching decisions
            </h2>
            <div className="mt-5 space-y-3">
              {data.decisions.length === 0 ? (
                <p className="text-sm text-[var(--muted)]">
                  No matching decision was returned.
                </p>
              ) : (
                data.decisions.map((decision) => (
                  <div
                    key={decision.id}
                    className="rounded-2xl border border-[var(--border)] bg-[var(--surface-muted)] p-4"
                  >
                    <div className="flex flex-wrap items-center justify-between gap-2">
                      <StatusBadge value={decision.outcome} />
                      <span className="font-mono text-[0.65rem] text-[var(--muted)]">
                        {decision.ruleCode}
                      </span>
                    </div>
                    <p className="mt-3 text-sm leading-6">
                      {decision.explanation}
                    </p>
                    <div className="mt-4 flex items-end justify-between gap-3 border-t border-[var(--border)] pt-3">
                      <span className="text-xs text-[var(--muted)]">
                        Difference
                      </span>
                      <span className="font-display text-2xl font-bold">
                        {formatMoney(decision.difference, decision.currency)}
                      </span>
                    </div>
                  </div>
                ))
              )}
            </div>
          </CardContent>
        </Card>
        <Card className="bg-[var(--ink)] text-white">
          <CardContent>
            <p className="utility-label text-white/45">Append-only history</p>
            <h2 className="mt-2 font-display text-4xl font-bold uppercase">
              Audit events
            </h2>
            <ol className="mt-6 space-y-5 border-l border-white/15 pl-5">
              {data.auditEvents.length === 0 ? (
                <li className="text-sm text-white/55">
                  No audit events were returned.
                </li>
              ) : (
                data.auditEvents.map((event) => (
                  <li key={event.id} className="relative">
                    <span className="absolute top-1 -left-[1.58rem] size-3 rounded-full border-2 border-[var(--ink)] bg-[var(--coral)]" />
                    <p className="font-bold">{titleCase(event.eventType)}</p>
                    <p className="mt-1 text-xs text-white/45">
                      {formatDate(event.occurredAt)} ·{" "}
                      {titleCase(event.actorType)}
                    </p>
                    <pre className="mt-3 rounded-xl bg-white/5 p-3 font-mono text-[0.65rem] whitespace-pre-wrap text-white/65">
                      {JSON.stringify(event.details, null, 2)}
                    </pre>
                  </li>
                ))
              )}
            </ol>
          </CardContent>
        </Card>
      </div>
    </>
  );
}
