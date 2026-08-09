import axe from "axe-core";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type { ReactNode } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { api } from "@/lib/api/client";
import {
  exceptionDetailFixture,
  pagedFixture,
  resultFixture,
} from "@/test/fixtures";

import ExceptionsPage from "./page";

vi.mock("next/navigation", () => ({
  useSearchParams: () => new URLSearchParams(),
}));

vi.mock("@/components/layout/workspace-gate", () => ({
  WorkspaceGate: ({ children }: { children: ReactNode }) => children,
}));

function renderPage() {
  const client = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  });
  return render(
    <QueryClientProvider client={client}>
      <ExceptionsPage />
    </QueryClientProvider>,
  );
}

describe("Exception workbench", () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it("filters the queue and records a resolution note", async () => {
    const user = userEvent.setup();
    const result = resultFixture();
    const listSpy = vi
      .spyOn(api, "exceptions")
      .mockResolvedValue(pagedFixture([result]));
    vi.spyOn(api, "exception").mockResolvedValue(
      exceptionDetailFixture(result),
    );
    const resolveSpy = vi
      .spyOn(api, "resolveException")
      .mockResolvedValue({ ...result, resolutionStatus: "RESOLVED" });

    renderPage();
    const note = await screen.findByRole("textbox", { name: "Operator note" });
    await user.type(note, "Evidence confirms the processor fee adjustment.");
    await user.click(screen.getByRole("button", { name: "Resolve exception" }));

    await waitFor(() =>
      expect(resolveSpy).toHaveBeenCalledWith(
        result.id,
        "Evidence confirms the processor fee adjustment.",
      ),
    );

    await user.click(
      screen.getByRole("combobox", { name: "Filter by resolution status" }),
    );
    await user.click(screen.getByRole("option", { name: "Resolved" }));
    await waitFor(() =>
      expect(listSpy).toHaveBeenLastCalledWith(0, 100, {
        resolutionStatus: "RESOLVED",
      }),
    );
  });

  it("reopens a resolved result with the operator reason", async () => {
    const user = userEvent.setup();
    const result = resultFixture({ resolutionStatus: "RESOLVED" });
    vi.spyOn(api, "exceptions").mockResolvedValue(pagedFixture([result]));
    vi.spyOn(api, "exception").mockResolvedValue(
      exceptionDetailFixture(result),
    );
    const reopenSpy = vi
      .spyOn(api, "reopenException")
      .mockResolvedValue({ ...result, resolutionStatus: "OPEN" });

    renderPage();
    const note = await screen.findByRole("textbox", { name: "Operator note" });
    await user.type(note, "New bank evidence requires another review.");
    await user.click(screen.getByRole("button", { name: "Reopen exception" }));

    await waitFor(() =>
      expect(reopenSpy).toHaveBeenCalledWith(
        result.id,
        "New bank evidence requires another review.",
      ),
    );
  });

  it("has no automatically detectable accessibility violations", async () => {
    const result = resultFixture();
    vi.spyOn(api, "exceptions").mockResolvedValue(pagedFixture([result]));
    vi.spyOn(api, "exception").mockResolvedValue(
      exceptionDetailFixture(result),
    );

    const { container } = renderPage();
    await screen.findByRole("textbox", { name: "Operator note" });
    const report = await axe.run(container, {
      rules: { "color-contrast": { enabled: false } },
    });
    expect(report.violations).toEqual([]);
  });
});
