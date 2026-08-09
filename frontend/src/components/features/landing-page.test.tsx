import axe from "axe-core";
import { act, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";

import { LandingPage } from "./landing-page";

const workspaceMock = vi.hoisted(() => vi.fn());

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: vi.fn() }),
}));

vi.mock("@/lib/hooks/use-workspace", () => ({
  useWorkspace: workspaceMock,
}));

function workspaceState(overrides: Record<string, unknown> = {}) {
  return {
    ready: true,
    hasWorkspace: false,
    meta: null,
    clear: vi.fn(),
    start: {
      mutate: vi.fn(),
      isPending: false,
      error: null,
    },
    ...overrides,
  };
}

describe("LandingPage", () => {
  afterEach(() => {
    vi.useRealTimers();
    vi.clearAllMocks();
  });

  it("reveals the cold-start explanation when session creation is slow", () => {
    vi.useFakeTimers();
    workspaceMock.mockReturnValue(
      workspaceState({
        start: {
          mutate: vi.fn(),
          isPending: true,
          error: null,
        },
      }),
    );

    render(<LandingPage />);
    expect(
      screen.queryByText("The backend is waking up."),
    ).not.toBeInTheDocument();

    act(() => vi.advanceTimersByTime(3_500));
    expect(screen.getByText("The backend is waking up.")).toBeInTheDocument();
    expect(screen.getByRole("status")).toHaveTextContent(
      "This can take several minutes",
    );
  });

  it("has no automatically detectable accessibility violations", async () => {
    workspaceMock.mockReturnValue(workspaceState());
    const { container } = render(<LandingPage />);

    const report = await axe.run(container, {
      rules: { "color-contrast": { enabled: false } },
    });
    expect(report.violations).toEqual([]);
  });
});
