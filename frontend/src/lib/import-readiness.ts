import type { ImportView, SourceType } from "@/lib/api/types";

export const ACTIVE_IMPORT_STATUSES = new Set([
  "VALIDATING",
  "QUEUED",
  "RUNNING",
]);

export const SUCCESSFUL_IMPORT_STATUSES = new Set([
  "COMPLETED",
  "COMPLETED_WITH_ERRORS",
  "DUPLICATE",
]);

const REQUIRED_SOURCES: SourceType[] = ["INTERNAL", "PROCESSOR", "BANK"];

export function isImportActive(item: ImportView) {
  return ACTIVE_IMPORT_STATUSES.has(item.status.toUpperCase());
}

export function duplicateCanonicalImportId(item: ImportView) {
  return item.status.toUpperCase() === "DUPLICATE" ? item.duplicateOfId : null;
}

export function importProgress(item: ImportView) {
  const processed = item.importedRows + item.quarantinedRows;
  const terminal =
    SUCCESSFUL_IMPORT_STATUSES.has(item.status.toUpperCase()) ||
    item.status.toUpperCase() === "FAILED";
  const percent =
    item.totalRows > 0
      ? Math.min(100, (processed / item.totalRows) * 100)
      : terminal
        ? 100
        : 0;
  return { processed, percent };
}

export function summarizeImportReadiness(
  items: ImportView[],
  requiredImportIds: string[] = [],
) {
  const active = items.filter(isImportActive);
  const failures = items.filter(
    (item) => item.status.toUpperCase() === "FAILED",
  );
  const requiredIdSet = new Set(requiredImportIds);
  const requiredItems =
    requiredImportIds.length > 0
      ? items.filter((item) => requiredIdSet.has(item.id))
      : items;
  const successfulRequiredItems = requiredItems.filter((item) =>
    SUCCESSFUL_IMPORT_STATUSES.has(item.status.toUpperCase()),
  );
  const successfulSources = new Set(
    successfulRequiredItems.map((item) => item.sourceType),
  );
  const readySourceCount = REQUIRED_SOURCES.filter((source) =>
    successfulSources.has(source),
  ).length;

  return {
    active,
    failures,
    readySourceCount,
    foundRequiredCount: requiredItems.length,
    ready:
      active.length === 0 &&
      readySourceCount === REQUIRED_SOURCES.length &&
      (requiredImportIds.length === 0 ||
        successfulRequiredItems.length === requiredImportIds.length),
  };
}
