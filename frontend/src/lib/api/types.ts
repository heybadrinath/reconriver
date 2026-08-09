import type { components } from "./schema";

export type SourceType = NonNullable<
  components["schemas"]["ImportView"]["sourceType"]
>;
export type ReconciliationOutcome = NonNullable<
  components["schemas"]["ResultView"]["outcome"]
>;
export type ResultScope = NonNullable<
  components["schemas"]["ResultView"]["resultScope"]
>;

export interface DemoSession {
  token: string;
  workspaceId: string;
  expiresAt: string;
  warning: string;
}

export interface ScenarioFile {
  fileName: string;
  sourceType: SourceType | null;
  rowCount: number;
  checksum: string | null;
  downloadUrl: string;
}

export interface Scenario {
  slug: string;
  name: string;
  purpose: string;
  description?: string;
  canonicalPaymentCount: number;
  expectedExceptionCount: number;
  availableFiles: ScenarioFile[];
  expectedReconciliationSummary: Record<string, number>;
  controlledFailureAfterChunks?: number | null;
  warning?: string;
}

export interface Paged<T> {
  items: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface ImportView {
  id: string;
  sourceType: SourceType;
  fileName: string;
  checksum: string;
  status: string;
  totalRows: number;
  importedRows: number;
  quarantinedRows: number;
  duplicateOfId: string | null;
  failureMessage?: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface ImportErrorView {
  id: string;
  rowNumber: number;
  errorCode: string;
  message: string;
  createdAt: string;
}

export interface ScenarioLoadResult {
  workspaceId: string;
  scenarioSlug: string;
  imports: ImportView[];
  controlledFailureAfterChunks?: number | null;
  message: string;
}

export interface RunView {
  id: string;
  status: string;
  totalWorkItems: number;
  processedItems: number;
  matchedCount: number;
  exceptionCount: number;
  progressPercent: number;
  startedAt: string | null;
  completedAt: string | null;
  createdAt: string;
  durationMillis: number | null;
  restartable: boolean;
  failureMessage: string | null;
}

export interface RunProgress {
  runId: string;
  status: string;
  total: number;
  processed: number;
  percent: number;
  readCount: number;
  writeCount: number;
  skipCount: number;
  startedAt: string | null;
  updatedAt: string;
  failureMessage: string | null;
  restartable: boolean;
}

export interface RunSummary {
  runId: string;
  totalResults: number;
  matchedCount: number;
  exceptionCount: number;
  matchedRate: number;
  openExceptions: number;
  unreconciledValue: number | string;
  settlementBatchCount: number;
  durationMillis: number | null;
  outcomes: Record<string, number>;
  rules: Record<string, number>;
}

export interface ResolutionEvent {
  id: string;
  action: string;
  note: string;
  createdAt: string;
}

export interface AuditEvent {
  id: string;
  aggregateType: string;
  aggregateId: string;
  eventType: string;
  actorType: string;
  details: Record<string, unknown>;
  occurredAt: string;
}

export interface ResultView {
  id: string;
  runId: string;
  resultScope: ResultScope;
  workKey: string;
  outcome: ReconciliationOutcome;
  ruleCode: string;
  explanation: string;
  resolutionStatus: string;
  internalTransactionId: string | null;
  processorTransactionId: string | null;
  settlementBatchId: string | null;
  bankSettlementId: string | null;
  internalPaymentReference: string | null;
  processorTransactionReference: string | null;
  settlementBatchReference: string | null;
  bankEntryReference: string | null;
  grossAmount: number | string | null;
  processorFee: number | string | null;
  netAmount: number | string | null;
  bankCredit: number | string | null;
  difference: number | string | null;
  currency: string;
  internalOccurredAt: string | null;
  processorEventAt: string | null;
  bankBookedAt: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface ExceptionDetailView {
  result: ResultView;
  resolutions: ResolutionEvent[];
  auditEvents: AuditEvent[];
}

export interface TraceNode {
  id?: string;
  reference?: string;
  amount?: number | string;
  currency?: string;
  occurredAt?: string;
  [key: string]: unknown;
}

export interface TransactionTrace {
  transactionId: string;
  internal: TraceNode | null;
  processor: TraceNode | null;
  settlementBatch: TraceNode | null;
  bank: TraceNode | null;
  refund?: TraceNode | null;
  decisions: ResultView[];
  auditEvents: AuditEvent[];
}

export interface ProblemDetails {
  type?: string;
  title?: string;
  status?: number;
  detail?: string;
  instance?: string;
  code?: string;
  violations?: Array<{ field?: string; message?: string }>;
}

export interface RiverFlow {
  id: string;
  resultId: string;
  runId: string;
  outcome: string;
  ruleCode: string;
  explanation: string;
  amount: number | string | null;
  currency: string;
  internalId: string | null;
  processorId: string | null;
  batchId: string | null;
  bankId: string | null;
}

type AssertNoMissingGeneratedFields<T extends never> = T;

export type ResultContractCoverage = AssertNoMissingGeneratedFields<
  Exclude<keyof components["schemas"]["ResultView"], keyof ResultView>
>;
export type ScenarioContractCoverage = AssertNoMissingGeneratedFields<
  Exclude<keyof components["schemas"]["ScenarioView"], keyof Scenario>
>;
export type ImportContractCoverage = AssertNoMissingGeneratedFields<
  Exclude<keyof components["schemas"]["ImportView"], keyof ImportView>
>;
export type TraceContractCoverage = AssertNoMissingGeneratedFields<
  Exclude<
    keyof components["schemas"]["TransactionTraceView"],
    keyof TransactionTrace
  >
>;
export type ScenarioLoadContractCoverage = AssertNoMissingGeneratedFields<
  Exclude<keyof components["schemas"]["LoadResponse"], keyof ScenarioLoadResult>
>;
