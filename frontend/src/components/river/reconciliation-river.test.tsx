import axe from "axe-core";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";

import { ReconciliationRiver } from "./reconciliation-river";
import type { ResultView } from "@/lib/api/types";

const results: ResultView[] = [
  {
    id: "result-1",
    runId: "run-1",
    resultScope: "ORDER",
    workKey: "ORDER:ORDER-1",
    outcome: "MATCHED",
    ruleCode: "EXACT_REFERENCE",
    explanation: "Reference, amount and currency agree.",
    resolutionStatus: "RESOLVED",
    internalTransactionId: "internal-1",
    processorTransactionId: "processor-1",
    settlementBatchId: "batch-1",
    bankSettlementId: "bank-1",
    internalPaymentReference: "ORDER-1",
    processorTransactionReference: "PAY-1",
    settlementBatchReference: "BATCH-1",
    bankEntryReference: "CREDIT-1",
    grossAmount: "120.00",
    processorFee: "3.50",
    netAmount: "116.50",
    bankCredit: "116.50",
    difference: "0.00",
    currency: "USD",
    internalOccurredAt: "2026-01-01T10:00:00Z",
    processorEventAt: "2026-01-01T10:01:00Z",
    bankBookedAt: "2026-01-02T08:00:00Z",
    createdAt: "2026-01-02T08:00:00Z",
    updatedAt: "2026-01-02T08:00:00Z",
  },
  {
    ...({} as ResultView),
    id: "result-2",
    runId: "run-1",
    resultScope: "ORDER",
    workKey: "ORDER:ORDER-2",
    outcome: "FEE_MISMATCH",
    ruleCode: "PROCESSOR_FEE_POLICY",
    explanation: "The processor fee exceeds policy.",
    resolutionStatus: "OPEN",
    internalTransactionId: "internal-2",
    processorTransactionId: "processor-2",
    settlementBatchId: "batch-2",
    bankSettlementId: "bank-2",
    internalPaymentReference: "ORDER-2",
    processorTransactionReference: "PAY-2",
    settlementBatchReference: "BATCH-2",
    bankEntryReference: "CREDIT-2",
    grossAmount: "80.00",
    processorFee: "6.00",
    netAmount: "74.00",
    bankCredit: "74.00",
    difference: "2.00",
    currency: "USD",
    internalOccurredAt: null,
    processorEventAt: null,
    bankBookedAt: null,
    createdAt: "2026-01-02T08:00:00Z",
    updatedAt: "2026-01-02T08:00:00Z",
  },
];

describe("ReconciliationRiver", () => {
  it("offers keyboard-selectable flows and an equivalent table", async () => {
    const user = userEvent.setup();
    const onSelect = vi.fn();
    render(<ReconciliationRiver results={results} onSelect={onSelect} />);

    expect(
      screen.getByRole("group", {
        name: /Reconciliation flows in mobile layout/i,
      }),
    ).toBeInTheDocument();

    const flow = screen.getByRole("button", { name: /Matched, \$120\.00/i });
    flow.focus();
    await user.keyboard("{Enter}");
    expect(onSelect).toHaveBeenCalledWith(
      expect.objectContaining({ resultId: "result-1" }),
    );

    await user.click(screen.getByText("View equivalent flow table"));
    expect(
      screen.getByRole("table", { name: /Equivalent data/i }),
    ).toBeInTheDocument();
    expect(screen.getAllByText("bank-1").length).toBeGreaterThan(0);
  });

  it("keeps mismatch outcomes out of the matched green branch", () => {
    const mismatchBase = results[1];
    if (!mismatchBase) throw new Error("Mismatch fixture is missing.");
    const mismatchResults = (
      ["AMOUNT_MISMATCH", "FEE_MISMATCH", "CURRENCY_MISMATCH"] as const
    ).map((outcome, index) => ({
      ...mismatchBase,
      id: `mismatch-${index}`,
      workKey: `ORDER:MISMATCH-${index}`,
      outcome,
    }));

    render(<ReconciliationRiver results={mismatchResults} />);

    const river = screen.getByRole("group", {
      name: /Payment reconciliation flows/i,
    });
    const mismatchFlows = within(river).getAllByRole("button", {
      name: /^Discrepancy,/i,
    });

    expect(mismatchFlows).toHaveLength(3);
    mismatchFlows.forEach((flow) => {
      expect(flow.querySelector('[stroke="var(--coral)"]')).not.toBeNull();
      expect(
        flow.querySelector('[stroke="var(--green)"], [fill="var(--green)"]'),
      ).toBeNull();
    });
  });

  it("has no automatically detectable accessibility violations", async () => {
    const { container } = render(<ReconciliationRiver results={results} />);
    const report = await axe.run(container, {
      rules: { "color-contrast": { enabled: false } },
    });
    expect(report.violations).toEqual([]);
  });
});
