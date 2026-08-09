import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { ReconcileProgress } from "./reconcile-progress";

describe("ReconcileProgress", () => {
  it("announces the status of every workflow step", () => {
    render(<ReconcileProgress current={2} completedThrough={1} />);

    expect(
      screen.getByRole("link", { name: "Completed: Choose data" }),
    ).not.toHaveAttribute("aria-current");
    expect(
      screen.getByRole("link", { name: "Current: Review sources" }),
    ).toHaveAttribute("aria-current", "step");
    expect(
      screen.getByRole("link", { name: "Not completed: Reconcile" }),
    ).not.toHaveAttribute("aria-current");
  });
});
