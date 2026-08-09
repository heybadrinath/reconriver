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
      await screen.findByRole("radio", { name: /Mixed exceptions/i }),
    );
    fireEvent.click(await screen.findByText("View 1 input file"));

    expect(
      await screen.findByText(/Supporting artifact · 1 row/),
    ).toBeVisible();
  });

  it("surfaces one load action and keeps the file inventory collapsed initially", async () => {
    vi.spyOn(api, "scenarios").mockResolvedValue({ items: [scenario] });
    vi.spyOn(api, "scenario").mockResolvedValue({
      ...scenario,
      availableFiles: [
        {
          fileName: "processor.csv",
          sourceType: "PROCESSOR",
          rowCount: 1000,
          checksum: "a".repeat(64),
          downloadUrl:
            "/api/v1/demo/scenarios/mixed-exceptions/files/processor.csv",
        },
      ],
    });
    const client = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    });

    const { container } = render(
      <QueryClientProvider client={client}>
        <ScenariosPage />
      </QueryClientProvider>,
    );

    expect(await screen.findByText("View 1 input file")).toBeVisible();
    expect(
      screen.getAllByRole("button", { name: "Load sources" }),
    ).toHaveLength(1);

    const inventory = container.querySelector("details");
    expect(inventory).not.toHaveAttribute("open");

    fireEvent.click(screen.getByText("View 1 input file"));
    expect(inventory).toHaveAttribute("open");
  });
});
