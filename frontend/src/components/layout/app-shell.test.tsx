import { render, screen, within } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { AppShell } from "./app-shell";

let pathname = "/overview";

vi.mock("next/navigation", () => ({
  usePathname: () => pathname,
}));

describe("AppShell navigation", () => {
  beforeEach(() => {
    pathname = "/overview";
  });

  it("uses aria-current only for a navigation item's exact URL", () => {
    pathname = "/runs/6d650ffc";

    render(<AppShell>Content</AppShell>);

    const navigation = screen.getByRole("navigation", {
      name: "Workspace navigation",
    });
    const reconcile = within(navigation).getByRole("link", {
      name: "Reconcile",
    });

    expect(reconcile).toHaveAttribute("data-active", "true");
    expect(reconcile).not.toHaveAttribute("aria-current");
  });

  it("marks the exact destination as the current page", () => {
    pathname = "/scenarios";

    render(<AppShell>Content</AppShell>);

    const navigation = screen.getByRole("navigation", {
      name: "Workspace navigation",
    });
    const reconcile = within(navigation).getByRole("link", {
      name: "Reconcile",
    });

    expect(reconcile).toHaveAttribute("data-active", "true");
    expect(reconcile).toHaveAttribute("aria-current", "page");
  });

  it("marks the system destination as the current page", () => {
    pathname = "/architecture";

    render(<AppShell>Content</AppShell>);

    expect(screen.getByRole("link", { name: "System" })).toHaveAttribute(
      "aria-current",
      "page",
    );
  });
});
