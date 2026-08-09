import type { ExceptionDetailView, Paged, ResultView } from "@/lib/api/types";

export function resultFixture(overrides: Partial<ResultView> = {}): ResultView {
  return {
    id: "result-1",
    runId: "run-1",
    resultScope: "ORDER",
    workKey: "ORDER:ORDER-1",
    outcome: "FEE_MISMATCH",
    ruleCode: "PROCESSOR_FEE_POLICY",
    explanation: "The processor fee exceeds the configured policy.",
    resolutionStatus: "OPEN",
    internalTransactionId: "internal-1",
    processorTransactionId: "processor-1",
    settlementBatchId: "batch-1",
    bankSettlementId: "bank-1",
    internalPaymentReference: "ORDER-1",
    processorTransactionReference: "PAY-1",
    settlementBatchReference: "BATCH-1",
    bankEntryReference: "CREDIT-1",
    grossAmount: "120.00",
    processorFee: "5.00",
    netAmount: "115.00",
    bankCredit: "115.00",
    difference: "1.50",
    currency: "USD",
    internalOccurredAt: "2026-01-01T10:00:00Z",
    processorEventAt: "2026-01-01T10:01:00Z",
    bankBookedAt: "2026-01-02T08:00:00Z",
    createdAt: "2026-01-02T08:00:00Z",
    updatedAt: "2026-01-02T08:00:00Z",
    ...overrides,
  };
}

export function pagedFixture<T>(items: T[]): Paged<T> {
  return {
    items,
    page: 0,
    size: Math.max(items.length, 1),
    totalElements: items.length,
    totalPages: items.length > 0 ? 1 : 0,
  };
}

export function exceptionDetailFixture(
  result: ResultView = resultFixture(),
): ExceptionDetailView {
  return {
    result,
    resolutions: [],
    auditEvents: [],
  };
}
