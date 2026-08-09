import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, waitFor } from "@testing-library/react";
import type { ReactNode } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { api } from "@/lib/api/client";
import type { RunSummary, RunView } from "@/lib/api/types";
import { pagedFixture } from "@/test/fixtures";

import OverviewPage from "./page";

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: vi.fn() }),
}));

vi.mock("@/components/layout/workspace-gate", () => ({
  WorkspaceGate: ({ children }: { children: ReactNode }) => children,
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

describe("Overview result loading", () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it("requests a backend-valid result page", async () => {
    vi.spyOn(api, "runs").mockResolvedValue(pagedFixture([completedRun]));
    vi.spyOn(api, "summary").mockResolvedValue(completedSummary);
    const resultsSpy = vi
      .spyOn(api, "results")
      .mockResolvedValue(pagedFixture([]));
    const client = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    });

    render(
      <QueryClientProvider client={client}>
        <OverviewPage />
      </QueryClientProvider>,
    );

    await waitFor(() => {
      expect(resultsSpy).toHaveBeenCalledWith("run-1", 0, 100);
    });
  });
});
