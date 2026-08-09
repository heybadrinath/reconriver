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

  it("moves mobile focus to the inspector after a queue selection", async () => {
    const user = userEvent.setup();
    const first = resultFixture();
    const second = resultFixture({
      id: "result-2",
      workKey: "ORDER:ORDER-2",
      internalPaymentReference: "ORDER-2",
      explanation: "The bank credit does not match the processor net amount.",
    });
    vi.spyOn(api, "exceptions").mockResolvedValue(
      pagedFixture([first, second]),
    );
    vi.spyOn(api, "exception").mockImplementation((id) =>
      Promise.resolve(
        exceptionDetailFixture(id === second.id ? second : first),
      ),
    );
    const scrollSpy = vi.spyOn(Element.prototype, "scrollIntoView");

    renderPage();
    await screen.findByText(first.explanation);
    await user.click(screen.getByRole("button", { name: /ORDER-2/i }));

    const inspector = screen.getByRole("region", {
      name: "Selected exception details",
    });
    await waitFor(() => {
      expect(scrollSpy).toHaveBeenCalledWith({
        behavior: "smooth",
        block: "start",
      });
      expect(inspector).toHaveFocus();
    });
    expect(await screen.findByText(second.explanation)).toBeVisible();
  });

  it("falls back to an item in the filtered queue when selection disappears", async () => {
    const user = userEvent.setup();
    const firstOpen = resultFixture();
    const secondOpen = resultFixture({
      id: "result-2",
      workKey: "ORDER:ORDER-2",
      internalPaymentReference: "ORDER-2",
      explanation: "This open result is selected before filtering.",
    });
    const resolved = resultFixture({
      id: "result-3",
      workKey: "ORDER:ORDER-3",
      internalPaymentReference: "ORDER-3",
      resolutionStatus: "RESOLVED",
      explanation: "This resolved result remains in the filtered queue.",
    });
    vi.spyOn(api, "exceptions").mockImplementation((_page, _size, filters) =>
      Promise.resolve(
        pagedFixture(
          filters?.resolutionStatus === "RESOLVED"
            ? [resolved]
            : [firstOpen, secondOpen],
        ),
      ),
    );
    vi.spyOn(api, "exception").mockImplementation((id) => {
      const result =
        id === resolved.id
          ? resolved
          : id === secondOpen.id
            ? secondOpen
            : firstOpen;
      return Promise.resolve(exceptionDetailFixture(result));
    });

    renderPage();
    await screen.findByText(firstOpen.explanation);
    await user.click(screen.getByRole("button", { name: /ORDER-2/i }));
    expect(await screen.findByText(secondOpen.explanation)).toBeVisible();

    await user.click(
      screen.getByRole("combobox", { name: "Filter by resolution status" }),
    );
    await user.click(screen.getByRole("option", { name: "Resolved" }));

    expect(await screen.findByText(resolved.explanation)).toBeVisible();
    expect(screen.queryByText(secondOpen.explanation)).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: /ORDER-3/i })).toHaveAttribute(
      "aria-pressed",
      "true",
    );
  });

  it("keeps detail tabs touch-sized with visible keyboard focus styles", async () => {
    const result = resultFixture();
    vi.spyOn(api, "exceptions").mockResolvedValue(pagedFixture([result]));
    vi.spyOn(api, "exception").mockResolvedValue(
      exceptionDetailFixture(result),
    );

    renderPage();
    const decision = await screen.findByRole("tab", { name: "Decision" });
    const timeline = screen.getByRole("tab", { name: "Audit timeline" });

    for (const tab of [decision, timeline]) {
      expect(tab).toHaveClass("min-h-11");
      expect(tab).toHaveClass("focus-visible:ring-3");
      expect(tab).toHaveClass("focus-visible:outline-none");
    }
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
