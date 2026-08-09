import { createElement } from "react";

import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { api } from "@/lib/api/client";
import type { RunProgress, RunSummary, RunView } from "@/lib/api/types";
import { pagedFixture, resultFixture } from "@/test/fixtures";

import { resultInspection, RunDetails } from "./run-details";

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: vi.fn() }),
}));

const completedRun = {
  id: "run-1",
  status: "COMPLETED",
  totalWorkItems: 1,
  processedItems: 1,
  matchedCount: 1,
  exceptionCount: 0,
  progressPercent: 100,
  startedAt: "2026-01-01T10:00:00Z",
  completedAt: "2026-01-01T10:01:00Z",
  createdAt: "2026-01-01T10:00:00Z",
  durationMillis: 60_000,
  restartable: false,
  failureMessage: null,
} satisfies RunView;

const completedProgress = {
  runId: "run-1",
  status: "COMPLETED",
  total: 1,
  processed: 1,
  percent: 100,
  readCount: 1,
  writeCount: 1,
  skipCount: 0,
  startedAt: "2026-01-01T10:00:00Z",
  updatedAt: "2026-01-01T10:01:00Z",
  failureMessage: null,
  restartable: false,
} satisfies RunProgress;

const completedSummary = {
  runId: "run-1",
  totalResults: 1,
  matchedCount: 1,
  exceptionCount: 0,
  matchedRate: 100,
  openExceptions: 0,
  unreconciledValue: 0,
  settlementBatchCount: 1,
  durationMillis: 60_000,
  outcomes: { MATCHED: 1 },
  rules: { EXACT_REFERENCE: 1 },
} satisfies RunSummary;

beforeEach(() => {
  vi.restoreAllMocks();
});

describe("resultInspection", () => {
  it("routes an open mismatch to the exception workbench", () => {
    expect(
      resultInspection(
        resultFixture({ outcome: "FEE_MISMATCH", resolutionStatus: "OPEN" }),
      ),
    ).toEqual({
      href: "/exceptions?selected=result-1",
      label: "Open",
    });
  });

  it("routes a resolved mismatch to its transaction evidence", () => {
    expect(
      resultInspection(
        resultFixture({
          outcome: "FEE_MISMATCH",
          resolutionStatus: "RESOLVED",
        }),
      ),
    ).toEqual({
      href: "/transactions/internal-1",
      label: "Trace",
    });
  });

  it("keeps a reopened mismatch in the exception workbench", () => {
    expect(
      resultInspection(
        resultFixture({
          outcome: "FEE_MISMATCH",
          resolutionStatus: "REOPENED",
        }),
      ),
    ).toEqual({
      href: "/exceptions?selected=result-1",
      label: "Reopened",
    });
  });

  it("requests a backend-valid result page", async () => {
    vi.spyOn(api, "run").mockResolvedValue(completedRun);
    vi.spyOn(api, "progress").mockResolvedValue(completedProgress);
    vi.spyOn(api, "summary").mockResolvedValue(completedSummary);
    const resultsSpy = vi
      .spyOn(api, "results")
      .mockResolvedValue(pagedFixture([]));
    const client = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    });

    render(
      createElement(
        QueryClientProvider,
        { client },
        createElement(RunDetails, { runId: "run-1" }),
      ),
    );

    await waitFor(() => {
      expect(resultsSpy).toHaveBeenCalledWith("run-1", 0, 100);
    });
  });
});
