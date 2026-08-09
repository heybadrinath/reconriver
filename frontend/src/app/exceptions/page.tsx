"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import * as Tabs from "@radix-ui/react-tabs";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  ArrowRight,
  Landmark,
  CircleCheck,
  History,
  ListFilter,
  FilePenLine,
  LoaderCircle,
  Store,
} from "lucide-react";
import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { z } from "zod";

import { PageIntro } from "@/components/layout/page-intro";
import { WorkspaceGate } from "@/components/layout/workspace-gate";
import { ApiErrorState, EmptyState } from "@/components/states/api-state";
import { StatusBadge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Select } from "@/components/ui/select";
import { api } from "@/lib/api/client";
import type { ExceptionDetailView, ResultView } from "@/lib/api/types";
import { cn, formatDate, formatMoney, titleCase } from "@/lib/utils";

const resolutionSchema = z.object({
  note: z
    .string()
    .trim()
    .min(10, "Add at least 10 characters explaining the operator decision.")
    .max(1_000, "Keep the note under 1,000 characters."),
});
type ResolutionFields = z.infer<typeof resolutionSchema>;

export default function ExceptionsPage() {
  return (
    <WorkspaceGate>
      <ExceptionWorkbench />
    </WorkspaceGate>
  );
}

function ExceptionWorkbench() {
  const params = useSearchParams();
  const queryClient = useQueryClient();
  const [selectedId, setSelectedId] = useState<string | null>(
    params.get("selected"),
  );
  const [resolutionStatus, setResolutionStatus] = useState("OPEN");
  const [outcome, setOutcome] = useState("all");
  const exceptions = useQuery({
    queryKey: ["exceptions", resolutionStatus, outcome],
    queryFn: () =>
      api.exceptions(0, 100, {
        ...(resolutionStatus === "all" ? {} : { resolutionStatus }),
        ...(outcome === "all" ? {} : { outcome }),
      }),
  });
  const items = exceptions.data?.items ?? [];
  const effectiveSelectedId = selectedId ?? items[0]?.id ?? null;
  const detail = useQuery({
    queryKey: ["exception", effectiveSelectedId],
    queryFn: () => api.exception(effectiveSelectedId ?? ""),
    enabled: Boolean(effectiveSelectedId),
  });
  const form = useForm<ResolutionFields>({
    resolver: zodResolver(resolutionSchema),
    defaultValues: { note: "" },
  });
  const resolution = useMutation({
    mutationFn: ({
      id,
      note,
      action,
    }: {
      id: string;
      note: string;
      action: "resolve" | "reopen";
    }) =>
      action === "resolve"
        ? api.resolveException(id, note)
        : api.reopenException(id, note),
    onMutate({ id }) {
      setSelectedId(id);
    },
    async onSuccess() {
      form.reset();
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ["exceptions"] }),
        queryClient.invalidateQueries({
          queryKey: ["exception", effectiveSelectedId],
        }),
        queryClient.invalidateQueries({ queryKey: ["run-summary"] }),
      ]);
    },
  });
  return (
    <>
      <PageIntro
        eyebrow="Step 4 · Explain and decide"
        title="Exception workbench"
        description="Compare source evidence, read the failed rule and record a traceable operator decision. Resolution changes state; it never rewrites the original result."
      />
      <div className="mb-3 flex flex-col gap-3 rounded-[var(--radius-card)] border border-[var(--border)] bg-white p-4 sm:flex-row sm:items-center">
        <ListFilter size={22} aria-hidden="true" />
        <div className="flex flex-1 flex-col gap-2 sm:flex-row">
          <Select
            label="Filter by resolution status"
            value={resolutionStatus}
            onValueChange={setResolutionStatus}
            className="w-full"
            options={[
              { value: "OPEN", label: "Open" },
              { value: "RESOLVED", label: "Resolved" },
              { value: "all", label: "All statuses" },
            ]}
          />
          <Select
            label="Filter by outcome"
            value={outcome}
            onValueChange={setOutcome}
            className="w-full"
            options={[
              { value: "all", label: "All outcomes" },
              { value: "FEE_MISMATCH", label: "Fee mismatch" },
              { value: "MISSING_BANK", label: "Missing bank" },
              { value: "DUPLICATE", label: "Duplicate" },
              { value: "LATE_SETTLEMENT", label: "Late settlement" },
              { value: "CURRENCY_MISMATCH", label: "Currency mismatch" },
              { value: "AMBIGUOUS", label: "Ambiguous" },
            ]}
          />
        </div>
        <p className="font-mono text-xs text-[var(--muted)]">
          {items.length} shown
        </p>
      </div>

      {exceptions.error ? (
        <ApiErrorState
          error={exceptions.error}
          onRetry={() => void exceptions.refetch()}
        />
      ) : exceptions.isPending ? (
        <Card>
          <CardContent className="flex items-center gap-3">
            <LoaderCircle
              className="animate-spin"
              size={24}
              aria-hidden="true"
            />
            Loading exception evidence…
          </CardContent>
        </Card>
      ) : items.length === 0 ? (
        <EmptyState
          title="No exceptions match these filters"
          description="Change the outcome or resolution filter. If the run was clean, there is nothing for an operator to resolve."
          action={
            <Button
              variant="secondary"
              onClick={() => {
                setOutcome("all");
                setResolutionStatus("all");
              }}
            >
              Clear filters
            </Button>
          }
        />
      ) : (
        <div className="grid gap-3 xl:grid-cols-[minmax(290px,.34fr)_minmax(0,.66fr)]">
          <Card className="h-fit overflow-hidden xl:sticky xl:top-24 xl:max-h-[calc(100vh-7rem)]">
            <div className="border-b border-[var(--border)] p-4">
              <p className="utility-label text-[var(--muted)]">
                Investigation queue
              </p>
            </div>
            <div className="max-h-[620px] overflow-y-auto xl:max-h-[calc(100vh-12rem)]">
              {items.map((item) => (
                <ExceptionListItem
                  key={item.id}
                  item={item}
                  selected={effectiveSelectedId === item.id}
                  onSelect={() => setSelectedId(item.id)}
                />
              ))}
            </div>
          </Card>
          <div>
            {detail.isPending ? (
              <Card>
                <CardContent className="grid min-h-96 place-items-center">
                  <LoaderCircle
                    className="animate-spin"
                    size={28}
                    aria-label="Loading exception"
                  />
                </CardContent>
              </Card>
            ) : detail.error ? (
              <ApiErrorState
                error={detail.error}
                onRetry={() => void detail.refetch()}
              />
            ) : detail.data ? (
              <ExceptionInspector
                detail={detail.data}
                form={form}
                pending={resolution.isPending}
                mutationError={resolution.error}
                onResolve={form.handleSubmit(({ note }) =>
                  resolution.mutate({
                    id: detail.data.result.id,
                    note,
                    action: "resolve",
                  }),
                )}
                onReopen={form.handleSubmit(({ note }) =>
                  resolution.mutate({
                    id: detail.data.result.id,
                    note,
                    action: "reopen",
                  }),
                )}
              />
            ) : null}
          </div>
        </div>
      )}
    </>
  );
}

function ExceptionListItem({
  item,
  selected,
  onSelect,
}: {
  item: ResultView;
  selected: boolean;
  onSelect: () => void;
}) {
  return (
    <button
      type="button"
      onClick={onSelect}
      aria-pressed={selected}
      className={cn(
        "w-full border-b border-[var(--border)] p-4 text-left last:border-0 focus-visible:ring-3 focus-visible:ring-[var(--focus)] focus-visible:outline-none focus-visible:ring-inset",
        selected
          ? "bg-[var(--ink)] text-white"
          : "hover:bg-[var(--surface-muted)]",
      )}
    >
      <div className="flex items-center justify-between gap-3">
        <StatusBadge value={item.outcome} />
        <ArrowRight size={17} aria-hidden="true" />
      </div>
      <p className="mt-4 truncate font-mono text-xs">
        {item.internalPaymentReference ??
          item.processorTransactionReference ??
          item.id}
      </p>
      <div className="mt-2 flex items-end justify-between gap-3">
        <p className="font-display text-2xl font-bold">
          {formatMoney(item.difference, item.currency)} diff.
        </p>
        <span
          className={cn(
            "text-[0.65rem] font-bold",
            selected ? "text-white/55" : "text-[var(--muted)]",
          )}
        >
          {titleCase(item.resolutionStatus)}
        </span>
      </div>
    </button>
  );
}

function SourceEvidence({
  icon: Icon,
  title,
  reference,
  amount,
  fee,
  date,
  missing = false,
}: {
  icon: typeof Store;
  title: string;
  reference: string | null;
  amount: React.ReactNode;
  fee?: React.ReactNode;
  date: string | null;
  missing?: boolean;
}) {
  return (
    <div
      className={cn(
        "rounded-2xl border p-4",
        missing
          ? "border-dashed border-[#d58e84] bg-[var(--coral-soft)]"
          : "border-[var(--border)] bg-[var(--surface-muted)]",
      )}
    >
      <div className="flex items-center gap-2">
        <Icon size={19} aria-hidden="true" />
        <h3 className="font-display text-2xl font-bold uppercase">{title}</h3>
      </div>
      {missing ? (
        <p className="mt-8 text-sm font-bold text-[var(--coral-strong)]">
          No source record was linked.
        </p>
      ) : (
        <>
          <p className="mt-8 font-display text-3xl font-bold">{amount}</p>
          {fee ? (
            <p className="mt-1 text-xs text-[var(--muted)]">Fee {fee}</p>
          ) : null}
          <p className="mt-4 font-mono text-[0.65rem] break-all text-[var(--muted)]">
            {reference}
          </p>
          <p className="mt-2 text-xs text-[var(--muted)]">{formatDate(date)}</p>
        </>
      )}
    </div>
  );
}

function ExceptionInspector({
  detail,
  form,
  pending,
  mutationError,
  onResolve,
  onReopen,
}: {
  detail: ExceptionDetailView;
  form: ReturnType<typeof useForm<ResolutionFields>>;
  pending: boolean;
  mutationError: unknown;
  onResolve: () => void;
  onReopen: () => void;
}) {
  const result = detail.result;
  const resolved = result.resolutionStatus.toUpperCase() === "RESOLVED";
  return (
    <div className="space-y-3">
      <Card>
        <CardContent>
          <div className="flex flex-col justify-between gap-4 sm:flex-row sm:items-start">
            <div>
              <p className="utility-label text-[var(--muted)]">
                Rule explanation
              </p>
              <h2 className="mt-2 font-display text-4xl font-bold uppercase">
                {titleCase(result.ruleCode)}
              </h2>
              <p className="mt-3 max-w-3xl text-sm leading-6 text-[var(--muted-strong)]">
                {result.explanation}
              </p>
            </div>
            <StatusBadge value={result.resolutionStatus} />
          </div>
          <dl className="mt-6 grid grid-cols-2 gap-3 rounded-2xl bg-[var(--coral-soft)] p-4 sm:grid-cols-4">
            <div>
              <dt className="utility-label text-[var(--muted)]">Difference</dt>
              <dd className="mt-2 font-display text-3xl font-bold text-[var(--coral-strong)]">
                {formatMoney(result.difference, result.currency)}
              </dd>
            </div>
            <div>
              <dt className="utility-label text-[var(--muted)]">Gross</dt>
              <dd className="mt-2 font-display text-3xl font-bold">
                {formatMoney(result.grossAmount, result.currency)}
              </dd>
            </div>
            <div>
              <dt className="utility-label text-[var(--muted)]">
                Processor fee
              </dt>
              <dd className="mt-2 font-display text-3xl font-bold">
                {formatMoney(result.processorFee, result.currency)}
              </dd>
            </div>
            <div>
              <dt className="utility-label text-[var(--muted)]">Bank credit</dt>
              <dd className="mt-2 font-display text-3xl font-bold">
                {formatMoney(result.bankCredit, result.currency)}
              </dd>
            </div>
          </dl>
        </CardContent>
      </Card>

      <Card>
        <CardContent>
          <p className="utility-label text-[var(--muted)]">Source comparison</p>
          <div className="mt-4 grid gap-3 md:grid-cols-3">
            <SourceEvidence
              icon={Store}
              title="Internal"
              reference={result.internalPaymentReference}
              amount={formatMoney(result.grossAmount, result.currency)}
              date={result.internalOccurredAt}
              missing={!result.internalTransactionId}
            />
            <SourceEvidence
              icon={ListFilter}
              title="Processor"
              reference={result.processorTransactionReference}
              amount={formatMoney(result.netAmount, result.currency)}
              fee={formatMoney(result.processorFee, result.currency)}
              date={result.processorEventAt}
              missing={!result.processorTransactionId}
            />
            <SourceEvidence
              icon={Landmark}
              title="Bank"
              reference={result.bankEntryReference}
              amount={formatMoney(result.bankCredit, result.currency)}
              date={result.bankBookedAt}
              missing={!result.bankSettlementId}
            />
          </div>
        </CardContent>
      </Card>

      <Card>
        <CardContent>
          <Tabs.Root defaultValue="decision">
            <Tabs.List
              className="mb-5 inline-flex rounded-full border border-[var(--border)] bg-[var(--surface-muted)] p-1"
              aria-label="Exception detail sections"
            >
              <Tabs.Trigger
                value="decision"
                className="rounded-full px-4 py-2 text-sm font-bold data-[state=active]:bg-[var(--ink)] data-[state=active]:text-white"
              >
                Decision
              </Tabs.Trigger>
              <Tabs.Trigger
                value="timeline"
                className="rounded-full px-4 py-2 text-sm font-bold data-[state=active]:bg-[var(--ink)] data-[state=active]:text-white"
              >
                Audit timeline
              </Tabs.Trigger>
            </Tabs.List>
            <Tabs.Content value="decision">
              <div className="flex items-start gap-3">
                <FilePenLine
                  className="mt-1 shrink-0"
                  size={22}

                  aria-hidden="true"
                />
                <div>
                  <h2 className="font-display text-3xl font-bold uppercase">
                    {resolved
                      ? "Reopen this exception"
                      : "Resolve this exception"}
                  </h2>
                  <p className="mt-1 text-sm text-[var(--muted)]">
                    The note becomes part of the append-only audit history.
                  </p>
                </div>
              </div>
              <form
                className="mt-5"
                onSubmit={(event) => {
                  event.preventDefault();
                  if (resolved) onReopen();
                  else onResolve();
                }}
              >
                <label
                  htmlFor="resolution-note"
                  className="mb-2 block text-sm font-bold"
                >
                  Operator note
                </label>
                <textarea
                  id="resolution-note"
                  rows={5}
                  className="w-full resize-y rounded-2xl border border-[var(--border-strong)] bg-white p-4 text-sm outline-none focus-visible:ring-3 focus-visible:ring-[var(--focus)]"
                  placeholder={
                    resolved
                      ? "Explain why this result needs another review…"
                      : "Describe the evidence and why this exception can be closed…"
                  }
                  {...form.register("note")}
                />
                <div className="mt-2 flex flex-col justify-between gap-3 sm:flex-row sm:items-center">
                  <p
                    className="text-xs font-bold text-[var(--critical)]"
                    role="alert"
                  >
                    {form.formState.errors.note?.message}
                  </p>
                  <Button
                    type="submit"
                    variant={resolved ? "secondary" : "primary"}
                    disabled={pending}
                  >
                    {pending ? (
                      <LoaderCircle
                        className="animate-spin"
                        size={18}
                        aria-hidden="true"
                      />
                    ) : resolved ? (
                      <History
                        size={18}

                        aria-hidden="true"
                      />
                    ) : (
                      <CircleCheck size={18} aria-hidden="true" />
                    )}
                    {resolved ? "Reopen exception" : "Resolve exception"}
                  </Button>
                </div>
              </form>
              {mutationError ? (
                <div className="mt-4">
                  <ApiErrorState error={mutationError} />
                </div>
              ) : null}
            </Tabs.Content>
            <Tabs.Content value="timeline">
              <ol className="space-y-3">
                {detail.resolutions.map((resolution) => (
                  <li
                    key={resolution.id}
                    className="flex gap-3 rounded-2xl border border-[var(--border)] bg-[var(--violet-soft)] p-4"
                  >
                    <span className="mt-1 size-3 shrink-0 rounded-full bg-[var(--violet)]" />
                    <div>
                      <p className="font-bold">
                        {titleCase(resolution.action)}
                      </p>
                      <p className="mt-1 text-xs text-[var(--muted)]">
                        {formatDate(resolution.createdAt)}
                      </p>
                      <p className="mt-2 text-sm leading-6">
                        {resolution.note}
                      </p>
                    </div>
                  </li>
                ))}
                {detail.auditEvents.length === 0 ? (
                  <li className="text-sm text-[var(--muted)]">
                    No audit events were returned for this exception.
                  </li>
                ) : (
                  detail.auditEvents.map((event) => (
                    <li
                      key={event.id}
                      className="flex gap-3 rounded-2xl border border-[var(--border)] bg-[var(--surface-muted)] p-4"
                    >
                      <span className="mt-1 size-3 shrink-0 rounded-full bg-[var(--ink)]" />
                      <div>
                        <p className="font-bold">
                          {titleCase(event.eventType)}
                        </p>
                        <p className="mt-1 text-xs text-[var(--muted)]">
                          {formatDate(event.occurredAt)} ·{" "}
                          {titleCase(event.actorType)}
                        </p>
                        <pre className="mt-2 font-mono text-[0.68rem] whitespace-pre-wrap text-[var(--muted-strong)]">
                          {JSON.stringify(event.details, null, 2)}
                        </pre>
                      </div>
                    </li>
                  ))
                )}
              </ol>
            </Tabs.Content>
          </Tabs.Root>
          <div className="mt-5 border-t border-[var(--border)] pt-5">
            <Button asChild variant="ghost">
              <Link
                href={`/transactions/${result.internalTransactionId ?? result.processorTransactionId ?? result.id}`}
              >
                Open complete transaction trace{" "}
                <ArrowRight size={18} aria-hidden="true" />
              </Link>
            </Button>
          </div>
        </CardContent>
      </Card>
    </div>
  );
}
