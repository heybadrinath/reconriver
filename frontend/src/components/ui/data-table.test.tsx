import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";

import { DataTable } from "./data-table";

describe("DataTable", () => {
  it("sorts and paginates through data using accessible controls", async () => {
    const user = userEvent.setup();
    const data = [{ name: "Gamma" }, { name: "Alpha" }, { name: "Beta" }];
    render(
      <DataTable
        data={data}
        columns={[{ accessorKey: "name", header: "Name" }]}
        caption="Example records"
        pageSize={2}
      />,
    );
    expect(
      screen.getByRole("table", { name: "Example records" }),
    ).toBeInTheDocument();
    expect(screen.getByText("Page 1 of 2")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Previous" })).toBeDisabled();
    await user.click(screen.getByRole("button", { name: /Name/i }));
    expect(screen.getByText("Alpha")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Next" }));
    expect(screen.getByText("Page 2 of 2")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Next" })).toBeDisabled();
    expect(screen.getByText("Gamma")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Previous" }));
    expect(screen.getByText("Alpha")).toBeInTheDocument();
  });
});
