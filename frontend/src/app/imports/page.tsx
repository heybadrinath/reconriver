"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import type { ColumnDef } from "@tanstack/react-table";
import {
  ArrowRight,
  CircleCheck,
  FileSpreadsheet,
  ShieldAlert,
  LoaderCircle,
  Upload,
  CircleAlert,
  X,
} from "lucide-react";
import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { useMemo, useRef, useState } from "react";
import { useForm } from "react-hook-form";
import { z } from "zod";

import { PageIntro } from "@/components/layout/page-intro";
import { WorkspaceGate } from "@/components/layout/workspace-gate";
import { ApiErrorState, EmptyState } from "@/components/states/api-state";
import { StatusBadge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { DataTable } from "@/components/ui/data-table";
import { Progress } from "@/components/ui/progress";
import { Select } from "@/components/ui/select";
import { api, getLoadedImportIds } from "@/lib/api/client";
import type { ImportErrorView, ImportView, SourceType } from "@/lib/api/types";
import {
  duplicateCanonicalImportId,
  importProgress,
  isImportActive,
  summarizeImportReadiness,
} from "@/lib/import-readiness";
import { formatCount, formatDate, titleCase } from "@/lib/utils";

export const uploadSchema = z.object({
  sourceType: z.enum(["INTERNAL", "PROCESSOR", "BANK"]),
  file: z
    .instanceof(File, { message: "Choose a CSV file." })
    .refine(
      (file) => file.size <= 10 * 1024 * 1024,
      "The public demo accepts files up to 10 MB.",
    )
    .refine(
      (file) =>
        file.name.toLowerCase().endsWith(".csv") || file.type === "text/csv",
      "Choose a .csv file. Other formats are not accepted.",
    ),
});

type UploadFields = z.infer<typeof uploadSchema>;

const columns: ColumnDef<ImportView>[] = [
  {
    accessorKey: "fileName",
    header: "File",
    cell: ({ row }) => {
      const canonicalId = duplicateCanonicalImportId(row.original);
      return (
        <div>
          <p className="font-bold">{row.original.fileName}</p>
          <p className="mt-1 font-mono text-[0.65rem] text-[var(--muted)]">
            {row.original.id}
          </p>
          {row.original.status.toUpperCase() === "DUPLICATE" ? (
            <p className="mt-2 text-xs font-bold text-[#6548a4]">
              File skipped · canonical import{" "}
              <span
                className="font-mono"
                title={canonicalId ?? "Canonical import ID was not returned"}
              >
                {canonicalId ? `${canonicalId.slice(0, 8)}…` : "unavailable"}
              </span>
            </p>
          ) : null}
        </div>
      );
    },
  },
  {
    accessorKey: "sourceType",
    header: "Source",
    cell: ({ getValue }) => titleCase(String(getValue())),
  },
  {
    accessorKey: "status",
    header: "Status",
    cell: ({ getValue }) => <StatusBadge value={String(getValue())} />,
  },
  {
    id: "progress",
    header: "Progress",
    cell: ({ row }) => {
      const progress = importProgress(row.original);
      return (
        <div className="min-w-32">
          <div className="mb-1 flex justify-between gap-3 font-mono text-[0.65rem] text-[var(--muted)]">
            <span>{Math.round(progress.percent)}%</span>
            <span>
              {formatCount(progress.processed)} /{" "}
              {formatCount(row.original.totalRows)}
            </span>
          </div>
          <Progress value={progress.percent} />
        </div>
      );
    },
  },
  {
    accessorKey: "importedRows",
    header: "Imported",
    cell: ({ getValue }) => formatCount(Number(getValue())),
  },
  {
    accessorKey: "quarantinedRows",
    header: "Quarantined",
    cell: ({ getValue }) => {
      const value = Number(getValue());
      return (
        <span
          className={value > 0 ? "font-bold text-[var(--coral-strong)]" : ""}
        >
          {formatCount(value)}
        </span>
      );
    },
  },
  {
    accessorKey: "checksum",
    header: "Checksum",
    cell: ({ getValue }) => (
      <span className="font-mono text-[0.68rem]">
        {String(getValue()).slice(0, 14)}…
      </span>
    ),
  },
  {
    accessorKey: "createdAt",
    header: "Created",
    cell: ({ getValue }) => formatDate(String(getValue())),
  },
];

const errorColumns: ColumnDef<ImportErrorView>[] = [
  {
    accessorKey: "rowNumber",
    header: "Row",
    cell: ({ getValue }) => formatCount(Number(getValue())),
  },
  {
    accessorKey: "errorCode",
    header: "Code",
    cell: ({ getValue }) => (
      <span className="font-mono text-xs">{String(getValue())}</span>
    ),
  },
  {
    accessorKey: "message",
    header: "Actionable error",
  },
  {
    accessorKey: "createdAt",
    header: "Detected",
    cell: ({ getValue }) => formatDate(String(getValue())),
  },
];

export default function ImportsPage() {
  return (
    <WorkspaceGate>
      <ImportsContent />
    </WorkspaceGate>
  );
}

function ImportsContent() {
  const searchParams = useSearchParams();
  const loadedFromScenario = searchParams.get("loaded") === "1";
  const loadedImportIds = getLoadedImportIds();
  const queryClient = useQueryClient();
  const inputRef = useRef<HTMLInputElement>(null);
  const [dragging, setDragging] = useState(false);
  const [headers, setHeaders] = useState<string[]>([]);
  const [selectedImport, setSelectedImport] = useState<ImportView | null>(null);
  const imports = useQuery({
    queryKey: ["imports"],
    queryFn: () => api.imports(0, 100),
    refetchInterval(query) {
      const items = query.state.data?.items ?? [];
      const foundLoadedImports =
        loadedImportIds.length > 0
          ? items.filter((item) => loadedImportIds.includes(item.id)).length
          : items.length;
      return items.some(isImportActive) ||
        (loadedFromScenario &&
          foundLoadedImports < (loadedImportIds.length || 3))
        ? 1_000
        : false;
    },
  });
  const form = useForm<UploadFields>({
    resolver: zodResolver(uploadSchema),
    defaultValues: { sourceType: "INTERNAL" },
  });
  const importErrors = useQuery({
    queryKey: ["import-errors", selectedImport?.id],
    queryFn: () => api.importErrors(selectedImport?.id ?? "", 0, 100),
    enabled: Boolean(selectedImport),
  });
  const selectedFile = form.watch("file");
  const sourceType = form.watch("sourceType");
  const upload = useMutation({
    mutationFn: ({ file, sourceType }: UploadFields) =>
      api.uploadImport(file, sourceType),
    async onSuccess() {
      form.reset({ sourceType: "INTERNAL" });
      setHeaders([]);
      if (inputRef.current) inputRef.current.value = "";
      await queryClient.invalidateQueries({ queryKey: ["imports"] });
    },
  });

  async function acceptFile(file: File | undefined) {
    if (!file) return;
    form.setValue("file", file, { shouldValidate: true, shouldDirty: true });
    if (
      file.size <= 10 * 1024 * 1024 &&
      file.name.toLowerCase().endsWith(".csv")
    ) {
      const firstChunk = await file.slice(0, 8_192).text();
      const firstLine = firstChunk.split(/\r?\n/, 1)[0] ?? "";
      setHeaders(
        firstLine
          .split(",")
          .map((header) => header.trim().replaceAll('"', ""))
          .filter(Boolean),
      );
    } else {
      setHeaders([]);
    }
  }

  const list = imports.data?.items ?? [];
  const quarantined = list.reduce(
    (total, item) => total + item.quarantinedRows,
    0,
  );
  const readiness = summarizeImportReadiness(list, loadedImportIds);
  const importColumns = useMemo<ColumnDef<ImportView>[]>(
    () => [
      ...columns,
      {
        id: "errors",
        header: "Errors",
        enableSorting: false,
        cell: ({ row }) => {
          const inspectable =
            row.original.quarantinedRows > 0 ||
            row.original.status.toUpperCase() === "FAILED";
          return inspectable ? (
            <Button
              size="sm"
              variant="ghost"
              onClick={() => setSelectedImport(row.original)}
            >
              View errors
            </Button>
          ) : (
            <span className="text-xs text-[var(--muted)]">None</span>
          );
        },
      },
    ],
    [],
  );

  return (
    <>
      <PageIntro
        eyebrow="Step 2 · Validate source files"
        title="Imports"
        description="Load prepared scenario files or upload synthetic CSV data. The browser performs basic safety checks; the Spring API remains authoritative for headers, rows, checksums and idempotency."
        actions={
          readiness.ready ? (
            <Button asChild variant="primary">
              <Link href="/runs">
                Continue to runs <ArrowRight size={18} aria-hidden="true" />
              </Link>
            </Button>
          ) : (
            <Button variant="secondary" disabled>
              {imports.isPending || readiness.active.length > 0 ? (
                <LoaderCircle
                  className="animate-spin motion-reduce:animate-none"
                  size={18}
                  aria-hidden="true"
                />
              ) : (
                <CircleAlert size={18} aria-hidden="true" />
              )}
              {imports.isPending
                ? "Checking imports"
                : readiness.active.length > 0
                  ? "Waiting for imports"
                  : `${readiness.readySourceCount} of 3 sources ready`}
            </Button>
          )
        }
      />

      {loadedFromScenario ? (
        <div
          className={`mb-3 flex items-start gap-3 rounded-2xl border p-4 ${readiness.ready ? "border-[#80bda2] bg-[var(--green-soft)]" : "border-[#d6bd63] bg-[var(--yellow-soft)]"}`}
          role="status"
        >
          {readiness.ready ? (
            <CircleCheck
              className="mt-0.5 shrink-0"
              size={20}
              aria-hidden="true"
            />
          ) : (
            <LoaderCircle
              className="mt-0.5 shrink-0 animate-spin motion-reduce:animate-none"
              size={20}
              aria-hidden="true"
            />
          )}
          <div>
            <p className="font-bold">
              {readiness.ready
                ? "Scenario files ready"
                : "Scenario import jobs are processing"}
            </p>
            <p className="text-sm text-[var(--muted-strong)]">
              {readiness.ready
                ? "All three source types reached a successful terminal state. Review the row counts before continuing."
                : `${readiness.readySourceCount} of 3 source types are ready. This page refreshes from the API until validation finishes.`}
            </p>
          </div>
        </div>
      ) : null}

      {readiness.active.length > 0 && !loadedFromScenario ? (
        <div
          className="mb-3 flex items-start gap-3 rounded-2xl border border-[#d6bd63] bg-[var(--yellow-soft)] p-4"
          role="status"
        >
          <LoaderCircle
            className="mt-0.5 shrink-0 animate-spin motion-reduce:animate-none"
            size={20}
            aria-hidden="true"
          />
          <div>
            <p className="font-bold">
              Validating {readiness.active.length} source{" "}
              {readiness.active.length === 1 ? "file" : "files"}
            </p>
            <p className="text-sm text-[var(--muted-strong)]">
              Status and row progress refresh directly from the API every
              second.
            </p>
          </div>
        </div>
      ) : null}

      {readiness.failures.length > 0 ? (
        <div
          className="mb-3 rounded-2xl border border-[#e5a89d] bg-[var(--coral-soft)] p-4"
          role="alert"
        >
          <div className="flex items-start gap-3">
            <CircleAlert
              className="mt-0.5 shrink-0"
              size={20}
              aria-hidden="true"
            />
            <div>
              <p className="font-bold">
                {readiness.failures.length} import{" "}
                {readiness.failures.length === 1 ? "failed" : "jobs failed"}
              </p>
              <ul className="mt-2 space-y-1 text-sm text-[var(--muted-strong)]">
                {readiness.failures.map((item) => (
                  <li key={item.id}>
                    <span className="font-bold">{item.fileName}:</span>{" "}
                    {item.failureMessage ??
                      "The API did not return a failure explanation."}
                  </li>
                ))}
              </ul>
            </div>
          </div>
        </div>
      ) : null}

      <div className="data-grid">
        <Card className="col-span-12 lg:col-span-7">
          <CardContent>
            <div className="flex items-center justify-between gap-3">
              <div>
                <p className="utility-label text-[var(--muted)]">
                  Synthetic CSV upload
                </p>
                <h2 className="mt-2 font-display text-4xl font-bold uppercase">
                  Add a source file
                </h2>
              </div>
              <span className="grid size-11 place-items-center rounded-full bg-[var(--surface-muted)]">
                <Upload size={22} aria-hidden="true" />
              </span>
            </div>
            <form
              className="mt-6 space-y-5"
              onSubmit={form.handleSubmit((values) => upload.mutate(values))}
            >
              <div>
                <label
                  className="mb-2 block text-sm font-bold"
                  htmlFor="source-type"
                >
                  Source type
                </label>
                <Select
                  value={sourceType}
                  onValueChange={(value) =>
                    form.setValue("sourceType", value as SourceType, {
                      shouldValidate: true,
                    })
                  }
                  label="Source type"
                  options={[
                    { value: "INTERNAL", label: "Internal ledger" },
                    { value: "PROCESSOR", label: "Payment processor" },
                    { value: "BANK", label: "Bank settlement" },
                  ]}
                  className="w-full"
                />
              </div>
              <div
                className={`grid min-h-52 place-items-center rounded-2xl border-2 border-dashed p-5 text-center transition-colors ${dragging ? "border-[var(--ink)] bg-[var(--yellow-soft)]" : "border-[var(--border-strong)] bg-[var(--surface-muted)]"}`}
                onDragEnter={(event) => {
                  event.preventDefault();
                  setDragging(true);
                }}
                onDragOver={(event) => event.preventDefault()}
                onDragLeave={(event) => {
                  event.preventDefault();
                  setDragging(false);
                }}
                onDrop={(event) => {
                  event.preventDefault();
                  setDragging(false);
                  void acceptFile(event.dataTransfer.files[0]);
                }}
              >
                <div>
                  <span className="mx-auto grid size-12 place-items-center rounded-full bg-white">
                    <FileSpreadsheet size={24} aria-hidden="true" />
                  </span>
                  <p className="mt-4 font-bold">
                    {selectedFile?.name ?? "Drop a CSV file here"}
                  </p>
                  <p className="mt-1 text-xs text-[var(--muted)]">
                    CSV only · 10 MB maximum · 25,000 public rows
                  </p>
                  <label className="mt-4 inline-flex min-h-10 cursor-pointer items-center rounded-full border border-[var(--border-strong)] bg-white px-4 text-xs font-bold focus-within:ring-3 focus-within:ring-[var(--focus)]">
                    Browse files
                    <input
                      ref={inputRef}
                      type="file"
                      accept=".csv,text/csv"
                      className="sr-only"
                      onChange={(event) =>
                        void acceptFile(event.target.files?.[0])
                      }
                    />
                  </label>
                </div>
              </div>
              {form.formState.errors.file ? (
                <p
                  className="flex items-center gap-2 text-sm font-bold text-[var(--critical)]"
                  role="alert"
                >
                  <CircleAlert size={18} aria-hidden="true" />
                  {form.formState.errors.file.message}
                </p>
              ) : null}
              {headers.length > 0 ? (
                <div className="rounded-2xl border border-[var(--border)] bg-white p-4">
                  <p className="utility-label text-[var(--muted)]">
                    Detected header preview
                  </p>
                  <div className="mt-3 flex flex-wrap gap-2">
                    {headers.map((header) => (
                      <span
                        key={header}
                        className="rounded-full bg-[var(--surface-muted)] px-3 py-1 font-mono text-[0.68rem]"
                      >
                        {header}
                      </span>
                    ))}
                  </div>
                  <p className="mt-3 text-xs text-[var(--muted)]">
                    The API will validate required columns and report exact
                    errors.
                  </p>
                </div>
              ) : null}
              <div className="flex flex-col gap-3 rounded-2xl border border-[#e5a89d] bg-[var(--coral-soft)] p-4 sm:flex-row sm:items-center">
                <ShieldAlert
                  className="shrink-0"
                  size={22}

                  aria-hidden="true"
                />
                <p className="flex-1 text-xs leading-5">
                  Use synthetic demonstration data only. Do not upload real
                  payment, customer, card or bank information.
                </p>
                <Button
                  type="submit"
                  variant="primary"
                  disabled={upload.isPending}
                >
                  {upload.isPending ? (
                    <LoaderCircle
                      className="animate-spin"
                      size={18}
                      aria-hidden="true"
                    />
                  ) : (
                    <Upload size={18} aria-hidden="true" />
                  )}
                  Validate &amp; import
                </Button>
              </div>
            </form>
            {upload.error ? (
              <div className="mt-4">
                <ApiErrorState error={upload.error} />
              </div>
            ) : null}
          </CardContent>
        </Card>

        <div className="col-span-12 grid gap-3 lg:col-span-5">
          <Card className="bg-[var(--ink)] text-white">
            <CardContent>
              <p className="utility-label text-white/50">Workspace intake</p>
              <p className="metric-number mt-8">{formatCount(list.length)}</p>
              <p className="mt-1 text-sm text-white/60">source file attempts</p>
              <dl className="mt-8 grid grid-cols-2 gap-3 border-t border-white/10 pt-5">
                <div>
                  <dt className="utility-label text-white/45">Rows accepted</dt>
                  <dd className="mt-1 font-display text-3xl font-bold">
                    {formatCount(
                      list.reduce(
                        (total, item) => total + item.importedRows,
                        0,
                      ),
                    )}
                  </dd>
                </div>
                <div>
                  <dt className="utility-label text-white/45">Quarantined</dt>
                  <dd className="mt-1 font-display text-3xl font-bold text-[var(--coral)]">
                    {formatCount(quarantined)}
                  </dd>
                </div>
              </dl>
            </CardContent>
          </Card>
          {quarantined > 0 ? (
            <Card className="bg-[var(--yellow-soft)]">
              <CardContent>
                <CircleAlert size={24} aria-hidden="true" />
                <h2 className="mt-5 font-display text-3xl font-bold uppercase">
                  {formatCount(quarantined)} rows were quarantined
                </h2>
                <p className="mt-2 text-sm leading-6 text-[var(--muted-strong)]">
                  Open the import error report to correct malformed rows before
                  relying on the reconciliation result.
                </p>
              </CardContent>
            </Card>
          ) : null}
        </div>
      </div>

      <section className="mt-3">
        <div className="mb-4 flex items-end justify-between gap-3">
          <div>
            <p className="utility-label text-[var(--muted)]">Import history</p>
            <h2 className="mt-1 font-display text-4xl font-bold uppercase">
              Validated files
            </h2>
          </div>
        </div>
        {imports.isPending ? (
          <Card>
            <CardContent>
              <LoaderCircle
                className="animate-spin"
                size={26}
                aria-label="Loading imports"
              />
            </CardContent>
          </Card>
        ) : imports.error ? (
          <ApiErrorState
            error={imports.error}
            onRetry={() => void imports.refetch()}
          />
        ) : list.length === 0 ? (
          <EmptyState
            title="No imports yet"
            description="Choose a prepared scenario or upload a synthetic CSV to create the first validated import."
            action={
              <Button asChild variant="secondary">
                <Link href="/scenarios">Choose scenario</Link>
              </Button>
            }
          />
        ) : (
          <DataTable
            data={list}
            columns={importColumns}
            caption="Validated imports"
          />
        )}
      </section>

      {selectedImport ? (
        <section className="mt-3" aria-labelledby="import-errors-heading">
          <Card className="border-[#e5a89d] bg-[var(--coral-soft)]">
            <CardContent>
              <div className="flex items-start justify-between gap-4">
                <div>
                  <p className="utility-label text-[var(--muted)]">
                    Import error report
                  </p>
                  <h2
                    id="import-errors-heading"
                    className="mt-1 font-display text-4xl font-bold uppercase"
                  >
                    {selectedImport.fileName}
                  </h2>
                  {selectedImport.failureMessage ? (
                    <p className="mt-2 text-sm text-[var(--critical)]">
                      {selectedImport.failureMessage}
                    </p>
                  ) : null}
                </div>
                <Button
                  size="sm"
                  variant="secondary"
                  onClick={() => setSelectedImport(null)}
                >
                  <X size={16} aria-hidden="true" /> Close report
                </Button>
              </div>
              <div className="mt-5">
                {importErrors.isPending ? (
                  <div className="flex items-center gap-3" role="status">
                    <LoaderCircle
                      className="animate-spin motion-reduce:animate-none"
                      size={22}
                      aria-hidden="true"
                    />
                    Loading row errors…
                  </div>
                ) : importErrors.error ? (
                  <ApiErrorState
                    error={importErrors.error}
                    onRetry={() => void importErrors.refetch()}
                  />
                ) : (importErrors.data?.items.length ?? 0) === 0 ? (
                  <p className="rounded-2xl border border-dashed border-[#d58e84] bg-white/55 p-4 text-sm">
                    No row-level errors were returned. Use the import failure
                    message above to correct the file and upload it again.
                  </p>
                ) : (
                  <DataTable
                    data={importErrors.data?.items ?? []}
                    columns={errorColumns}
                    caption={`Row errors for ${selectedImport.fileName}`}
                    pageSize={10}
                  />
                )}
              </div>
            </CardContent>
          </Card>
        </section>
      ) : null}
    </>
  );
}
