import { beforeEach, describe, expect, it, vi } from "vitest";

import {
  api,
  ApiError,
  CONTROLLED_FAILURE_KEY,
  LOADED_IMPORT_IDS_KEY,
  SESSION_EXPIRED_EVENT,
  SESSION_TOKEN_KEY,
  classifyApiError,
} from "./client";

describe("API client", () => {
  beforeEach(() => {
    window.sessionStorage.clear();
    vi.restoreAllMocks();
  });

  it("maps a network failure to a recoverable backend state", async () => {
    vi.spyOn(globalThis, "fetch").mockRejectedValue(new TypeError("offline"));

    await expect(api.scenarios()).rejects.toMatchObject({
      status: 0,
      problem: { code: "BACKEND_UNAVAILABLE" },
    });
  });

  it("preserves Problem Details from a temporarily unavailable service", async () => {
    vi.spyOn(globalThis, "fetch").mockResolvedValue(
      new Response(
        JSON.stringify({
          title: "Database unavailable",
          detail: "The service is starting. Try again shortly.",
          status: 503,
          code: "DATABASE_UNAVAILABLE",
        }),
        {
          status: 503,
          headers: { "content-type": "application/problem+json" },
        },
      ),
    );

    const error = await api.runs().catch((reason: unknown) => reason);
    expect(error).toBeInstanceOf(ApiError);
    expect(classifyApiError(error)).toEqual({
      title: "Service temporarily unavailable",
      message: "The service is starting. Try again shortly.",
      action: "Retry",
    });
  });

  it("clears and announces an expired authenticated session", async () => {
    window.sessionStorage.setItem(SESSION_TOKEN_KEY, "expired-token");
    const expired = vi.fn();
    window.addEventListener(SESSION_EXPIRED_EVENT, expired, { once: true });
    vi.spyOn(globalThis, "fetch").mockResolvedValue(
      new Response(JSON.stringify({ title: "Unauthorized", status: 401 }), {
        status: 401,
        headers: { "content-type": "application/problem+json" },
      }),
    );

    await expect(api.runs()).rejects.toMatchObject({ status: 401 });
    expect(window.sessionStorage.getItem(SESSION_TOKEN_KEY)).toBeNull();
    expect(expired).toHaveBeenCalledOnce();
  });

  it("adds the current bearer token only to authenticated calls", async () => {
    const fetchMock = vi.spyOn(globalThis, "fetch").mockResolvedValue(
      new Response(
        JSON.stringify({
          items: [],
          page: 0,
          size: 20,
          totalElements: 0,
          totalPages: 0,
        }),
        { status: 200, headers: { "content-type": "application/json" } },
      ),
    );
    window.sessionStorage.setItem(SESSION_TOKEN_KEY, "token-123");

    await api.runs();
    const init = fetchMock.mock.calls[0]?.[1];
    expect(new Headers(init?.headers).get("Authorization")).toBe(
      "Bearer token-123",
    );
  });

  it("sends upload source type in the documented query parameter", async () => {
    const fetchMock = vi.spyOn(globalThis, "fetch").mockResolvedValue(
      new Response(JSON.stringify({ id: "import-1" }), {
        status: 202,
        headers: { "content-type": "application/json" },
      }),
    );
    const file = new File(["id,amount\n1,10"], "internal.csv", {
      type: "text/csv",
    });

    await api.uploadImport(file, "INTERNAL");

    expect(fetchMock.mock.calls[0]?.[0]).toBe(
      "http://localhost:8080/api/v1/imports?sourceType=INTERNAL",
    );
    const body = fetchMock.mock.calls[0]?.[1]?.body;
    expect(body).toBeInstanceOf(FormData);
    expect((body as FormData).get("sourceType")).toBeNull();
  });

  it("uses a loaded recovery control exactly once for the next run", async () => {
    const fetchMock = vi
      .spyOn(globalThis, "fetch")
      .mockResolvedValueOnce(
        new Response(
          JSON.stringify({
            workspaceId: "workspace-1",
            scenarioSlug: "failure-recovery",
            imports: [
              { id: "import-internal" },
              { id: "import-processor" },
              { id: "import-bank" },
            ],
            controlledFailureAfterChunks: 2,
            message: "Loaded",
          }),
          { status: 202, headers: { "content-type": "application/json" } },
        ),
      )
      .mockImplementation(() =>
        Promise.resolve(
          new Response(JSON.stringify({ id: "run-1" }), {
            status: 202,
            headers: { "content-type": "application/json" },
          }),
        ),
      );

    await api.loadScenario("failure-recovery");
    expect(window.sessionStorage.getItem(CONTROLLED_FAILURE_KEY)).toBe("2");
    expect(
      JSON.parse(window.sessionStorage.getItem(LOADED_IMPORT_IDS_KEY)!),
    ).toEqual(["import-internal", "import-processor", "import-bank"]);

    await api.startRun();
    expect(fetchMock.mock.calls[1]?.[1]?.body).toBe(
      JSON.stringify({ controlledFailureAfterChunks: 2 }),
    );
    expect(window.sessionStorage.getItem(CONTROLLED_FAILURE_KEY)).toBeNull();

    await api.startRun();
    expect(fetchMock.mock.calls[2]?.[1]?.body).toBe(JSON.stringify({}));
  });

  it("keeps restart bodyless and clears a leftover recovery control", async () => {
    window.sessionStorage.setItem(CONTROLLED_FAILURE_KEY, "3");
    const fetchMock = vi.spyOn(globalThis, "fetch").mockResolvedValue(
      new Response(JSON.stringify({ id: "run-1", status: "QUEUED" }), {
        status: 202,
        headers: { "content-type": "application/json" },
      }),
    );

    await api.restartRun("run-1");

    expect(fetchMock.mock.calls[0]?.[1]?.body).toBeUndefined();
    expect(window.sessionStorage.getItem(CONTROLLED_FAILURE_KEY)).toBeNull();
  });
});
