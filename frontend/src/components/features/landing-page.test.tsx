import axe from "axe-core";
import { act, render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";

import { LandingPage } from "./landing-page";

const workspaceMock = vi.hoisted(() => vi.fn());
const routerPush = vi.hoisted(() => vi.fn());

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: routerPush }),
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
    expect(screen.getByText("The service is starting.")).toBeInTheDocument();
    expect(screen.getByRole("status")).toHaveTextContent(
      "This can take several minutes after a period of inactivity",
    );
  });

  it("starts a workspace from the primary action", async () => {
    const user = userEvent.setup();
    const mutate = vi.fn();
    workspaceMock.mockReturnValue(
      workspaceState({
        start: {
          mutate,
          isPending: false,
          error: null,
        },
      }),
    );

    render(<LandingPage />);
    await user.click(
      screen.getAllByRole("button", { name: /start workspace/i })[0]!,
    );

    expect(mutate).toHaveBeenCalledWith(
      undefined,
      expect.objectContaining({ onSuccess: expect.any(Function) }),
    );
  });

  it("opens an existing workspace without creating another one", async () => {
    const user = userEvent.setup();
    const mutate = vi.fn();
    workspaceMock.mockReturnValue(
      workspaceState({
        hasWorkspace: true,
        start: {
          mutate,
          isPending: false,
          error: null,
        },
      }),
    );

    render(<LandingPage />);
    await user.click(screen.getByRole("button", { name: /open workspace/i }));

    expect(routerPush).toHaveBeenCalledWith("/scenarios");
    expect(mutate).not.toHaveBeenCalled();
  });

  it("updates the explanation when a rule is selected", async () => {
    const user = userEvent.setup();
    workspaceMock.mockReturnValue(workspaceState());

    render(<LandingPage />);
    await user.click(screen.getByRole("button", { name: "Fee discrepancy" }));

    expect(
      screen.getByText(
        "The processor fee differs from the configured policy, so the result is routed for review.",
      ),
    ).toBeInTheDocument();
  });

  it("uses product language without project framing", () => {
    workspaceMock.mockReturnValue(workspaceState());
    const { container } = render(<LandingPage />);

    expect(container).toHaveTextContent("Deterministic sample");
    expect(container).toHaveTextContent("1,244");
    expect(container).toHaveTextContent("1,007");
    expect(container).toHaveTextContent("237");
    expect(container.textContent).not.toMatch(
      /\b(portfolio|demo|production)\b/i,
    );
    const navigation = screen.getByRole("navigation", {
      name: "Primary navigation",
    });
    expect(
      within(navigation).getByRole("link", { name: "Product" }),
    ).not.toHaveAttribute("aria-current");
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
