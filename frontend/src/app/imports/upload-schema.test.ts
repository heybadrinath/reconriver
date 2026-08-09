import { describe, expect, it } from "vitest";

import { uploadSchema } from "./page";

describe("uploadSchema", () => {
  it("rejects non-CSV files", () => {
    const result = uploadSchema.safeParse({
      sourceType: "INTERNAL",
      file: new File(["private"], "payments.xlsx", {
        type: "application/vnd.ms-excel",
      }),
    });
    expect(result.success).toBe(false);
  });

  it("rejects files over the public 10 MB limit", () => {
    const result = uploadSchema.safeParse({
      sourceType: "BANK",
      file: new File([new Uint8Array(10 * 1024 * 1024 + 1)], "bank.csv", {
        type: "text/csv",
      }),
    });
    expect(result.success).toBe(false);
  });

  it("accepts a synthetic CSV within the public limit", () => {
    const result = uploadSchema.safeParse({
      sourceType: "PROCESSOR",
      file: new File(
        ["processor_transaction_id,amount\npay-1,12.00"],
        "processor.csv",
        { type: "text/csv" },
      ),
    });
    expect(result.success).toBe(true);
  });
});
