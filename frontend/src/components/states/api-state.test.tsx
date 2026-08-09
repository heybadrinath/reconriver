import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { ApiErrorState, ColdStartState } from "./api-state";
import { ApiError } from "@/lib/api/client";

describe("ApiErrorState", () => {
  it("gives an expired session a direct recovery instruction", () => {
    render(
      <ApiErrorState error={new ApiError(401, { title: "Unauthorized" })} />,
    );
    expect(screen.getByText("Workspace expired")).toBeInTheDocument();
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
    expect(screen.getByText("Request limit reached")).toBeInTheDocument();
    expect(
      screen.getByText(/sending requests too quickly/i),
    ).toBeInTheDocument();
  });

  it("explains a delayed service start without deployment-plan language", () => {
    render(<ColdStartState />);

    expect(screen.getByText("The service is starting.")).toBeInTheDocument();
    expect(screen.getByRole("status")).toHaveTextContent(
      "This can take several minutes after a period of inactivity.",
    );
  });
});
