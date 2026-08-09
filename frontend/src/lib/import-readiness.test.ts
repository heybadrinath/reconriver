import { describe, expect, it } from "vitest";

import type { ImportView, SourceType } from "@/lib/api/types";

import {
  duplicateCanonicalImportId,
  importProgress,
  summarizeImportReadiness,
} from "./import-readiness";

function importFixture(
  sourceType: SourceType,
  status: string,
  overrides: Partial<ImportView> = {},
): ImportView {
  return {
    id: `${sourceType}-${status}`,
    sourceType,
    fileName: `${sourceType.toLowerCase()}.csv`,
    checksum: "checksum",
    status,
    totalRows: 100,
    importedRows: status === "COMPLETED" ? 100 : 0,
    quarantinedRows: 0,
    duplicateOfId: null,
    createdAt: "2026-01-01T00:00:00Z",
    updatedAt: "2026-01-01T00:00:00Z",
    ...overrides,
  };
}

describe("import readiness", () => {
  it("waits until each required source is terminal-successful", () => {
    const items = [
      importFixture("INTERNAL", "COMPLETED"),
      importFixture("PROCESSOR", "RUNNING", { importedRows: 40 }),
      importFixture("BANK", "QUEUED"),
    ];

    expect(summarizeImportReadiness(items)).toMatchObject({
      ready: false,
      readySourceCount: 1,
    });
    expect(summarizeImportReadiness(items).active).toHaveLength(2);
  });

  it("accepts completed-with-errors and duplicate terminals while surfacing failures", () => {
    const items = [
      importFixture("INTERNAL", "COMPLETED"),
      importFixture("PROCESSOR", "COMPLETED_WITH_ERRORS", {
        importedRows: 98,
        quarantinedRows: 2,
      }),
      importFixture("BANK", "DUPLICATE"),
      importFixture("BANK", "FAILED", { failureMessage: "Malformed row" }),
    ];
    const summary = summarizeImportReadiness(items);

    expect(summary.ready).toBe(true);
    expect(summary.readySourceCount).toBe(3);
    expect(summary.failures).toHaveLength(1);
  });

  it("reports progress from API row counts", () => {
    expect(
      importProgress(
        importFixture("PROCESSOR", "RUNNING", {
          importedRows: 35,
          quarantinedRows: 5,
        }),
      ),
    ).toEqual({ processed: 40, percent: 40 });
  });

  it("does not reuse older successful imports while a newly loaded scenario is appearing", () => {
    const oldItems = [
      importFixture("INTERNAL", "COMPLETED", { id: "old-internal" }),
      importFixture("PROCESSOR", "COMPLETED", { id: "old-processor" }),
      importFixture("BANK", "COMPLETED", { id: "old-bank" }),
    ];
    const requiredIds = ["new-internal", "new-processor", "new-bank"];

    expect(summarizeImportReadiness(oldItems, requiredIds)).toMatchObject({
      foundRequiredCount: 0,
      readySourceCount: 0,
      ready: false,
    });
  });

  it("identifies the canonical import behind a skipped duplicate", () => {
    const duplicate = importFixture("BANK", "DUPLICATE", {
      duplicateOfId: "9ef48231-d32b-4d48-87f0-c9112bd4d2b3",
    });

    expect(duplicateCanonicalImportId(duplicate)).toBe(
      "9ef48231-d32b-4d48-87f0-c9112bd4d2b3",
    );
    expect(
      duplicateCanonicalImportId(importFixture("BANK", "COMPLETED")),
    ).toBeNull();
  });
});
