import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { ApiErrorState } from "./api-state";
import { ApiError } from "@/lib/api/client";

describe("ApiErrorState", () => {
  it("gives an expired session a direct recovery instruction", () => {
    render(
      <ApiErrorState error={new ApiError(401, { title: "Unauthorized" })} />,
    );
    expect(screen.getByText("Demo session expired")).toBeInTheDocument();
    expect(
      screen.getByText("Start a new workspace to continue."),
    ).toBeInTheDocument();
  });

  it("explains a rate limit instead of showing a generic error", () => {
    render(
      <ApiErrorState
        error={new ApiError(429, { title: "Too many requests" })}
      />,
    );
    expect(screen.getByText("Demo limit reached")).toBeInTheDocument();
    expect(
      screen.getByText(/sending requests too quickly/i),
    ).toBeInTheDocument();
  });
});
