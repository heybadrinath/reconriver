import type {
  AuditEvent,
  DemoSession,
  ExceptionDetailView,
  ImportErrorView,
  ImportView,
  Paged,
  ProblemDetails,
  ResultView,
  RunProgress,
  RunSummary,
  RunView,
  Scenario,
  ScenarioLoadResult,
  SourceType,
  TransactionTrace,
} from "./types";

const API_BASE_URL = (
  process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080"
).replace(/\/$/, "");

export const SESSION_TOKEN_KEY = "reconriver.demo.token";
export const SESSION_META_KEY = "reconriver.demo.session";
export const CONTROLLED_FAILURE_KEY =
  "reconriver.demo.controlledFailureAfterChunks";
export const LOADED_IMPORT_IDS_KEY = "reconriver.demo.loadedImportIds";
export const SESSION_EXPIRED_EVENT = "reconriver:session-expired";

export class ApiError extends Error {
  constructor(
    public readonly status: number,
    public readonly problem: ProblemDetails,
  ) {
    super(
      problem.detail ?? problem.title ?? `The API returned status ${status}.`,
    );
    this.name = "ApiError";
  }
}

export function getSessionToken() {
  if (typeof window === "undefined") return null;
  return window.sessionStorage.getItem(SESSION_TOKEN_KEY);
}

export function storeSession(session: DemoSession) {
  window.sessionStorage.removeItem(CONTROLLED_FAILURE_KEY);
  window.sessionStorage.removeItem(LOADED_IMPORT_IDS_KEY);
  window.sessionStorage.setItem(SESSION_TOKEN_KEY, session.token);
  window.sessionStorage.setItem(
    SESSION_META_KEY,
    JSON.stringify({
      workspaceId: session.workspaceId,
      expiresAt: session.expiresAt,
      warning: session.warning,
    }),
  );
}

export function clearSession() {
  if (typeof window === "undefined") return;
  window.sessionStorage.removeItem(SESSION_TOKEN_KEY);
  window.sessionStorage.removeItem(SESSION_META_KEY);
  window.sessionStorage.removeItem(CONTROLLED_FAILURE_KEY);
  window.sessionStorage.removeItem(LOADED_IMPORT_IDS_KEY);
}

export function getLoadedImportIds() {
  if (typeof window === "undefined") return [];
  const raw = window.sessionStorage.getItem(LOADED_IMPORT_IDS_KEY);
  if (!raw) return [];
  try {
    const value = JSON.parse(raw) as unknown;
    return Array.isArray(value)
      ? value.filter((id): id is string => typeof id === "string")
      : [];
  } catch {
    return [];
  }
}

function storeControlledFailure(value: number | null | undefined) {
  if (typeof window === "undefined") return;
  if (value == null) {
    window.sessionStorage.removeItem(CONTROLLED_FAILURE_KEY);
  } else {
    window.sessionStorage.setItem(CONTROLLED_FAILURE_KEY, String(value));
  }
}

function readControlledFailure() {
  if (typeof window === "undefined") return null;
  const raw = window.sessionStorage.getItem(CONTROLLED_FAILURE_KEY);
  if (raw === null) return null;
  const value = Number(raw);
  return Number.isInteger(value) && value > 0 ? value : null;
}

function toQuery(
  params: Record<string, string | number | boolean | undefined>,
) {
  const query = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== "") query.set(key, String(value));
  });
  const rendered = query.toString();
  return rendered ? `?${rendered}` : "";
}

async function request<T>(
  path: string,
  init: RequestInit = {},
  authenticated = true,
): Promise<T> {
  const headers = new Headers(init.headers);
  if (
    !(init.body instanceof FormData) &&
    init.body &&
    !headers.has("Content-Type")
  ) {
    headers.set("Content-Type", "application/json");
  }
  headers.set("Accept", "application/json");

  if (authenticated) {
    const token = getSessionToken();
    if (token) headers.set("Authorization", `Bearer ${token}`);
  }

  let response: Response;
  try {
    response = await fetch(`${API_BASE_URL}${path}`, {
      ...init,
      headers,
      cache: "no-store",
    });
  } catch {
    throw new ApiError(0, {
      code: "BACKEND_UNAVAILABLE",
      title: "Backend unavailable",
      detail:
        "The reconciliation service could not be reached. It may still be waking up; try again in a moment.",
    });
  }

  if (!response.ok) {
    const contentType = response.headers.get("content-type") ?? "";
    const problem = contentType.includes("json")
      ? ((await response.json()) as ProblemDetails)
      : ({
          title: response.statusText,
          detail: `The API returned status ${response.status}.`,
        } satisfies ProblemDetails);
    if (authenticated && [401, 403].includes(response.status)) {
      clearSession();
      if (typeof window !== "undefined") {
        window.dispatchEvent(new Event(SESSION_EXPIRED_EVENT));
      }
    }
    throw new ApiError(response.status, problem);
  }

  if (response.status === 204) return undefined as T;
  return (await response.json()) as T;
}

async function download(path: string, fileName: string) {
  const headers = new Headers();
  const token = getSessionToken();
  if (token) headers.set("Authorization", `Bearer ${token}`);
  const response = await fetch(`${API_BASE_URL}${path}`, { headers });
  if (!response.ok) {
    throw new ApiError(response.status, {
      title: "Download failed",
      detail: `The report could not be downloaded (status ${response.status}).`,
    });
  }
  const blob = await response.blob();
  const objectUrl = URL.createObjectURL(blob);
  const anchor = document.createElement("a");
  anchor.href = objectUrl;
  anchor.download = fileName;
  anchor.click();
  URL.revokeObjectURL(objectUrl);
}

export const api = {
  createSession: () =>
    request<DemoSession>("/api/v1/demo/sessions", { method: "POST" }, false),
  scenarios: () =>
    request<{ items: Scenario[] }>("/api/v1/demo/scenarios", {}, false),
  scenario: (slug: string) =>
    request<Scenario>(
      `/api/v1/demo/scenarios/${encodeURIComponent(slug)}`,
      {},
      false,
    ),
  loadScenario: (slug: string) =>
    request<ScenarioLoadResult>(
      `/api/v1/demo/scenarios/${encodeURIComponent(slug)}/load`,
      { method: "POST" },
    ).then((result) => {
      storeControlledFailure(result.controlledFailureAfterChunks);
      if (typeof window !== "undefined") {
        window.sessionStorage.setItem(
          LOADED_IMPORT_IDS_KEY,
          JSON.stringify(result.imports.map((item) => item.id)),
        );
      }
      return result;
    }),
  downloadScenarioFile: (slug: string, fileName: string) =>
    download(
      `/api/v1/demo/scenarios/${encodeURIComponent(slug)}/files/${encodeURIComponent(fileName)}`,
      fileName,
    ),

  imports: (page = 0, size = 20) =>
    request<Paged<ImportView>>(`/api/v1/imports${toQuery({ page, size })}`),
  importById: (id: string) => request<ImportView>(`/api/v1/imports/${id}`),
  importErrors: (id: string, page = 0, size = 50) =>
    request<Paged<ImportErrorView>>(
      `/api/v1/imports/${id}/errors${toQuery({ page, size })}`,
    ),
  uploadImport: (file: File, sourceType: SourceType) => {
    const body = new FormData();
    body.append("file", file);
    return request<ImportView>(`/api/v1/imports${toQuery({ sourceType })}`, {
      method: "POST",
      body,
    });
  },

  runs: (page = 0, size = 20) =>
    request<Paged<RunView>>(
      `/api/v1/reconciliation-runs${toQuery({ page, size })}`,
    ),
  run: (id: string) => request<RunView>(`/api/v1/reconciliation-runs/${id}`),
  startRun: async () => {
    const controlledFailureAfterChunks = readControlledFailure();
    const run = await request<RunView>("/api/v1/reconciliation-runs", {
      method: "POST",
      body: JSON.stringify(
        controlledFailureAfterChunks === null
          ? {}
          : { controlledFailureAfterChunks },
      ),
    });
    storeControlledFailure(null);
    return run;
  },
  restartRun: async (id: string) => {
    storeControlledFailure(null);
    return request<RunView>(`/api/v1/reconciliation-runs/${id}/restart`, {
      method: "POST",
    });
  },
  progress: (id: string) =>
    request<RunProgress>(`/api/v1/reconciliation-runs/${id}/progress`),
  summary: (id: string) =>
    request<RunSummary>(`/api/v1/reconciliation-runs/${id}/summary`),
  results: (id: string, page = 0, size = 100) =>
    request<Paged<ResultView>>(
      `/api/v1/reconciliation-runs/${id}/results${toQuery({ page, size })}`,
    ),
  downloadReport: (id: string) =>
    download(
      `/api/v1/reconciliation-runs/${id}/report.csv`,
      `reconciliation-${id}.csv`,
    ),

  exceptions: (
    page = 0,
    size = 50,
    filters: {
      outcome?: string;
      resolutionStatus?: string;
    } = {},
  ) =>
    request<Paged<ResultView>>(
      `/api/v1/exceptions${toQuery({
        page,
        size,
        outcome: filters.outcome,
        status: filters.resolutionStatus,
      })}`,
    ),
  exception: (id: string) =>
    request<ExceptionDetailView>(`/api/v1/exceptions/${id}`),
  resolveException: (id: string, note: string) =>
    request<ResultView>(`/api/v1/exceptions/${id}/resolve`, {
      method: "POST",
      body: JSON.stringify({ note }),
    }),
  reopenException: (id: string, note: string) =>
    request<ResultView>(`/api/v1/exceptions/${id}/reopen`, {
      method: "POST",
      body: JSON.stringify({ note }),
    }),

  trace: (id: string) =>
    request<TransactionTrace>(`/api/v1/transactions/${id}/trace`),
  auditEvents: (
    page = 0,
    size = 50,
    filters: { eventType?: string; aggregateType?: string } = {},
  ) =>
    request<Paged<AuditEvent>>(
      `/api/v1/audit-events${toQuery({ page, size, ...filters })}`,
    ),
};

export function classifyApiError(error: unknown) {
  if (!(error instanceof ApiError)) {
    return {
      title: "Request failed",
      message: "The request failed before the service returned a response.",
      action: "Try again",
    };
  }
  if (error.status === 401 || error.status === 403) {
    return {
      title: "Demo session expired",
      message: "Start a new workspace to continue.",
      action: "Start new workspace",
    };
  }
  if (error.status === 429) {
    return {
      title: "Demo limit reached",
      message:
        "This workspace is sending requests too quickly. Wait a moment and retry.",
      action: "Retry",
    };
  }
  if (error.status === 503) {
    return {
      title: "Service temporarily unavailable",
      message:
        error.problem.detail ??
        "The backend or database is unavailable. Try again after it recovers.",
      action: "Retry",
    };
  }
  return {
    title: error.problem.title ?? "Request failed",
    message: error.message,
    action: "Retry",
  };
}
