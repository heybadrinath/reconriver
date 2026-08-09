import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { fireEvent, render, screen } from "@testing-library/react";
import type { ReactNode } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { api } from "@/lib/api/client";
import type { Scenario } from "@/lib/api/types";

import ScenariosPage from "./page";

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: vi.fn() }),
}));

vi.mock("@/components/layout/workspace-gate", () => ({
  WorkspaceGate: ({ children }: { children: ReactNode }) => children,
}));

const scenario = {
  slug: "mixed-exceptions",
  name: "Mixed exceptions",
  purpose: "Exercise the investigation workflow.",
  description: "A deterministic synthetic scenario.",
  canonicalPaymentCount: 1000,
  expectedExceptionCount: 237,
  expectedReconciliationSummary: { MATCHED: 1007, AMOUNT_MISMATCH: 237 },
  controlledFailureAfterChunks: null,
  warning: "Synthetic data only.",
  availableFiles: [],
} satisfies Scenario;

describe("Scenario file details", () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it("renders support artifacts whose source type and checksum are null", async () => {
    vi.spyOn(api, "scenarios").mockResolvedValue({ items: [scenario] });
    vi.spyOn(api, "scenario").mockResolvedValue({
      ...scenario,
      availableFiles: [
        {
          fileName: "scenario_manifest.json",
          sourceType: null,
          rowCount: 1,
          checksum: null,
          downloadUrl:
            "/api/v1/demo/scenarios/mixed-exceptions/files/scenario_manifest.json",
        },
      ],
    });
    const client = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    });

    render(
      <QueryClientProvider client={client}>
        <ScenariosPage />
      </QueryClientProvider>,
    );

    fireEvent.click(
      await screen.findByRole("button", { name: /Mixed exceptions/i }),
    );

    expect(
      await screen.findByText(/Supporting artifact · 1 row/),
    ).toBeVisible();
  });
});
