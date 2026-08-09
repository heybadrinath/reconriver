"use client";

import { ArrowDown, Funnel, Info } from "lucide-react";
import { useMemo, useState } from "react";

import { Select } from "@/components/ui/select";
import type { ResultView, RiverFlow } from "@/lib/api/types";
import { cn, formatMoney, titleCase } from "@/lib/utils";

type FlowTone = "matched" | "discrepancy" | "late" | "refund" | "unresolved";

function toneFor(flow: RiverFlow): FlowTone {
  const outcome = flow.outcome.toUpperCase();
  if (outcome.includes("REFUND")) return "refund";
  if (outcome.includes("LATE")) return "late";
  if (outcome === "MATCHED") return "matched";
  if (outcome.includes("UNRESOLVED") || outcome.includes("AMBIGUOUS"))
    return "unresolved";
  return "discrepancy";
}

const tones: Record<FlowTone, { color: string; dash?: string; label: string }> =
  {
    matched: { color: "var(--green)", label: "Matched" },
    discrepancy: { color: "var(--coral)", label: "Discrepancy" },
    late: { color: "var(--yellow)", label: "Late settlement" },
    refund: { color: "var(--violet)", label: "Refund" },
    unresolved: { color: "#8b9391", dash: "5 7", label: "Unresolved" },
  };

function toFlows(results: ResultView[]): RiverFlow[] {
  return results.map((result) => ({
    id:
      result.internalTransactionId ??
      result.processorTransactionId ??
      result.id,
    resultId: result.id,
    runId: result.runId,
    outcome: result.outcome,
    ruleCode: result.ruleCode,
    explanation: result.explanation,
    amount: result.grossAmount,
    currency: result.currency,
    internalId: result.internalTransactionId,
    processorId: result.processorTransactionId,
    batchId: result.settlementBatchId,
    bankId: result.bankSettlementId,
  }));
}

export function ReconciliationRiver({
  results,
  onSelect,
  compact = false,
}: {
  results: ResultView[];
  onSelect?: (flow: RiverFlow) => void;
  compact?: boolean;
}) {
  const [filter, setFilter] = useState("all");
  const [activeId, setActiveId] = useState<string | null>(null);
  const flows = useMemo(
    () => toFlows(results).slice(0, compact ? 8 : 14),
    [results, compact],
  );
  const visible = useMemo(
    () => flows.filter((flow) => filter === "all" || toneFor(flow) === filter),
    [filter, flows],
  );
  const active =
    visible.find((flow) => flow.resultId === activeId) ?? visible[0] ?? null;

  return (
    <section aria-labelledby="river-heading">
      <div className="mb-4 flex flex-col justify-between gap-3 sm:flex-row sm:items-center">
        <div>
          <div className="flex items-center gap-2">
            <h2
              id="river-heading"
              className="font-display text-3xl leading-none font-bold uppercase"
            >
              Reconciliation River
            </h2>
            <Info
              size={17}
              className="text-[var(--muted)]"
              aria-hidden="true"
            />
          </div>
          <p className="mt-1 text-sm text-[var(--muted)]">
            Select a flow to inspect its complete transaction trace.
          </p>
        </div>
        <div className="flex items-center gap-2">
          <Funnel size={18} aria-hidden="true" />
          <Select
            label="Filter reconciliation flows"
            value={filter}
            onValueChange={setFilter}
            options={[
              { value: "all", label: "All outcomes" },
              { value: "matched", label: "Matched" },
              { value: "discrepancy", label: "Discrepancies" },
              { value: "late", label: "Late" },
              { value: "refund", label: "Refunds" },
              { value: "unresolved", label: "Unresolved" },
            ]}
            className="min-w-44"
          />
        </div>
      </div>

      {visible.length === 0 ? (
        <div className="grid min-h-48 place-items-center rounded-2xl border border-dashed border-white/25 bg-[#1d1f1f] p-6 text-center text-white">
          <div>
            <p className="font-display text-2xl font-bold uppercase">
              No flows match this filter
            </p>
            <p className="mt-1 text-sm text-white/65">
              Choose another outcome to restore the river.
            </p>
          </div>
        </div>
      ) : (
        <>
          <div className="hidden overflow-hidden rounded-[1.35rem] border border-black bg-[var(--ink)] p-4 text-white md:block">
            <div className="grid grid-cols-4 gap-2 border-b border-white/10 pb-3 text-center font-mono text-[0.65rem] font-semibold tracking-[0.11em] text-white/65 uppercase">
              <span>Internal ledger</span>
              <span>Processor</span>
              <span>Settlement batch</span>
              <span>Bank</span>
            </div>
            <svg
              viewBox={`0 0 1000 ${Math.max(250, visible.length * 38 + 55)}`}
              role="group"
              aria-labelledby="river-svg-title river-svg-description"
              className={cn("mt-3 h-auto w-full", compact && "max-h-[380px]")}
            >
              <title id="river-svg-title">Payment reconciliation flows</title>
              <desc id="river-svg-description">
                Internal payment records flow through the processor and
                settlement batch to the bank. Color and line style identify the
                outcome; the table after this graphic contains the same data.
              </desc>
              {[125, 375, 625, 875].map((x) => (
                <line
                  key={x}
                  x1={x}
                  x2={x}
                  y1="10"
                  y2="100%"
                  stroke="rgba(255,255,255,.08)"
                />
              ))}
              {visible.map((flow, index) => {
                const tone = toneFor(flow);
                const style = tones[tone];
                const y = 34 + index * 38;
                const isActive = active?.resultId === flow.resultId;
                const missingBank = !flow.bankId;
                const refundOffset = tone === "refund" ? 13 : 0;
                return (
                  <g
                    key={flow.resultId}
                    role="button"
                    tabIndex={0}
                    aria-label={`${style.label}, ${formatMoney(flow.amount, flow.currency)}, rule ${titleCase(flow.ruleCode)}`}
                    onFocus={() => setActiveId(flow.resultId)}
                    onMouseEnter={() => setActiveId(flow.resultId)}
                    onClick={() => onSelect?.(flow)}
                    onKeyDown={(event) => {
                      if (event.key === "Enter" || event.key === " ") {
                        event.preventDefault();
                        onSelect?.(flow);
                      }
                    }}
                    className="cursor-pointer outline-none"
                  >
                    <title>{`${style.label}: ${flow.id}, ${formatMoney(flow.amount, flow.currency)}`}</title>
                    {tone === "discrepancy" ? (
                      <>
                        <path
                          d={`M125 ${y} C220 ${y} 280 ${y} 440 ${y}`}
                          fill="none"
                          stroke={style.color}
                          strokeWidth={isActive ? 7 : 4}
                        />
                        <path
                          d={`M485 ${y} C570 ${y} 725 ${y} ${missingBank ? 785 : 875} ${y}`}
                          fill="none"
                          stroke={style.color}
                          strokeWidth={isActive ? 7 : 4}
                        />
                        <circle cx="463" cy={y} r="6" fill="var(--coral)" />
                      </>
                    ) : (
                      <path
                        d={`M125 ${y} C245 ${y} 275 ${y + refundOffset} 375 ${y + refundOffset} S520 ${y + refundOffset} 625 ${y + refundOffset} S760 ${y} ${missingBank ? 800 : 875} ${y}`}
                        fill="none"
                        stroke={style.color}
                        strokeWidth={isActive ? 7 : 4}
                        strokeDasharray={style.dash}
                        strokeLinecap="round"
                        className="river-flow motion-safe:animate-[river_1.4s_ease-out_forwards] motion-safe:[stroke-dashoffset:40] motion-reduce:animate-none"
                      />
                    )}
                    {[125, 375, 625, ...(missingBank ? [] : [875])].map((x) => (
                      <circle
                        key={x}
                        cx={x}
                        cy={x === 375 || x === 625 ? y + refundOffset : y}
                        r={isActive ? 7 : 5}
                        fill={style.color}
                        stroke="#101112"
                        strokeWidth="3"
                      />
                    ))}
                  </g>
                );
              })}
              <style>{`@keyframes river { from { stroke-dasharray: 4 800; } to { stroke-dasharray: 800 0; } }`}</style>
            </svg>
            {active ? (
              <div
                className="mt-2 flex flex-wrap items-center justify-between gap-3 rounded-2xl border border-white/10 bg-white/5 px-4 py-3"
                aria-live="polite"
              >
                <div>
                  <p className="font-mono text-[0.65rem] tracking-[0.1em] text-white/55 uppercase">
                    Focused flow · {tones[toneFor(active)].label}
                  </p>
                  <p className="mt-1 text-sm font-bold">{active.explanation}</p>
                </div>
                <div className="text-right">
                  <p className="font-display text-2xl font-bold">
                    {formatMoney(active.amount, active.currency)}
                  </p>
                  <p className="font-mono text-[0.65rem] text-white/55">
                    {active.id}
                  </p>
                </div>
              </div>
            ) : null}
          </div>

          <div
            className="space-y-3 md:hidden"
            aria-label="Reconciliation flows in mobile layout"
          >
            {visible.map((flow) => {
              const style = tones[toneFor(flow)];
              const stages = [
                ["Internal", flow.internalId],
                ["Processor", flow.processorId],
                ["Batch", flow.batchId],
                ["Bank", flow.bankId],
              ] as const;
              return (
                <button
                  key={flow.resultId}
                  type="button"
                  className="w-full rounded-[1.25rem] border border-[var(--border)] bg-white p-4 text-left focus-visible:ring-3 focus-visible:ring-[var(--focus)] focus-visible:outline-none"
                  onClick={() => onSelect?.(flow)}
                >
                  <div className="mb-3 flex items-center justify-between gap-3">
                    <span className="font-bold">{style.label}</span>
                    <span className="font-display text-2xl font-bold">
                      {formatMoney(flow.amount, flow.currency)}
                    </span>
                  </div>
                  <div className="space-y-1.5">
                    {stages.map(([label, id], index) => (
                      <div key={label}>
                        <div className="flex items-center gap-3 rounded-xl bg-[var(--surface-muted)] p-3">
                          <span
                            className="size-2.5 shrink-0 rounded-full"
                            style={{
                              backgroundColor: id ? style.color : "#8b9391",
                            }}
                          />
                          <span className="w-20 text-xs font-extrabold tracking-wide uppercase">
                            {label}
                          </span>
                          <span className="truncate font-mono text-[0.68rem] text-[var(--muted)]">
                            {id ?? "Missing"}
                          </span>
                        </div>
                        {index < stages.length - 1 ? (
                          <ArrowDown
                            className="mx-auto my-1 text-[var(--muted)]"
                            size={16}
                            aria-hidden="true"
                          />
                        ) : null}
                      </div>
                    ))}
                  </div>
                </button>
              );
            })}
          </div>
        </>
      )}

      <details className="mt-4 rounded-2xl border border-[var(--border)] bg-white p-4">
        <summary className="cursor-pointer font-bold focus-visible:ring-3 focus-visible:ring-[var(--focus)] focus-visible:outline-none">
          View equivalent flow table
        </summary>
        <div className="mt-4 overflow-x-auto">
          <table className="w-full min-w-[780px] border-collapse text-left text-sm">
            <caption className="sr-only">
              Equivalent data for the Reconciliation River
            </caption>
            <thead>
              <tr className="border-b border-[var(--border-strong)] font-mono text-[0.65rem] tracking-wider uppercase">
                <th scope="col" className="py-3 pr-4">
                  Outcome
                </th>
                <th scope="col" className="py-3 pr-4">
                  Internal
                </th>
                <th scope="col" className="py-3 pr-4">
                  Processor
                </th>
                <th scope="col" className="py-3 pr-4">
                  Batch
                </th>
                <th scope="col" className="py-3 pr-4">
                  Bank
                </th>
                <th scope="col" className="py-3 text-right">
                  Amount
                </th>
              </tr>
            </thead>
            <tbody>
              {visible.map((flow) => (
                <tr
                  key={flow.resultId}
                  className="border-b border-[var(--border)] last:border-0"
                >
                  <th scope="row" className="py-3 pr-4 font-bold">
                    {tones[toneFor(flow)].label}
                  </th>
                  <td className="py-3 pr-4 font-mono text-xs">
                    {flow.internalId ?? "Missing"}
                  </td>
                  <td className="py-3 pr-4 font-mono text-xs">
                    {flow.processorId ?? "Missing"}
                  </td>
                  <td className="py-3 pr-4 font-mono text-xs">
                    {flow.batchId ?? "Missing"}
                  </td>
                  <td className="py-3 pr-4 font-mono text-xs">
                    {flow.bankId ?? "Missing"}
                  </td>
                  <td className="py-3 text-right font-bold">
                    {formatMoney(flow.amount, flow.currency)}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </details>
    </section>
  );
}
