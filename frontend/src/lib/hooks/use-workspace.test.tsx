import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, renderHook, waitFor } from "@testing-library/react";
import type { ReactNode } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import {
  api,
  SESSION_EXPIRED_EVENT,
  SESSION_META_KEY,
  SESSION_TOKEN_KEY,
} from "@/lib/api/client";

import { useWorkspace } from "./use-workspace";

function createWrapper() {
  const client = new QueryClient({
    defaultOptions: { mutations: { retry: false } },
  });
  return function Wrapper({ children }: { children: ReactNode }) {
    return (
      <QueryClientProvider client={client}>{children}</QueryClientProvider>
    );
  };
}

describe("useWorkspace", () => {
  beforeEach(() => {
    window.sessionStorage.clear();
    vi.restoreAllMocks();
  });

  it("stores a new session and clears it on operator request", async () => {
    const session = {
      token: "demo-token",
      workspaceId: "workspace-1",
      expiresAt: "2099-01-01T00:00:00Z",
      warning: "Synthetic data only.",
    };
    vi.spyOn(api, "createSession").mockResolvedValue(session);
    const { result } = renderHook(() => useWorkspace(), {
      wrapper: createWrapper(),
    });

    await waitFor(() => expect(result.current.ready).toBe(true));
    act(() => result.current.start.mutate());
    await waitFor(() => expect(result.current.hasWorkspace).toBe(true));

    expect(window.sessionStorage.getItem(SESSION_TOKEN_KEY)).toBe(
      session.token,
    );
    expect(result.current.meta?.workspaceId).toBe(session.workspaceId);

    act(() => result.current.clear());
    expect(result.current.hasWorkspace).toBe(false);
    expect(window.sessionStorage.getItem(SESSION_TOKEN_KEY)).toBeNull();
    expect(window.sessionStorage.getItem(SESSION_META_KEY)).toBeNull();
  });

  it("rejects an expired stored workspace during hydration", async () => {
    window.sessionStorage.setItem(SESSION_TOKEN_KEY, "expired-token");
    window.sessionStorage.setItem(
      SESSION_META_KEY,
      JSON.stringify({
        workspaceId: "workspace-old",
        expiresAt: "2000-01-01T00:00:00Z",
        warning: "Expired",
      }),
    );

    const { result } = renderHook(() => useWorkspace(), {
      wrapper: createWrapper(),
    });
    await waitFor(() => expect(result.current.ready).toBe(true));

    expect(result.current.hasWorkspace).toBe(false);
    expect(result.current.meta).toBeNull();
    expect(window.sessionStorage.getItem(SESSION_TOKEN_KEY)).toBeNull();
    expect(window.sessionStorage.getItem(SESSION_META_KEY)).toBeNull();
  });

  it("drops the active workspace when the API announces expiry", async () => {
    window.sessionStorage.setItem(SESSION_TOKEN_KEY, "active-token");
    window.sessionStorage.setItem(
      SESSION_META_KEY,
      JSON.stringify({
        workspaceId: "workspace-1",
        expiresAt: "2099-01-01T00:00:00Z",
        warning: "Synthetic data only.",
      }),
    );
    const { result } = renderHook(() => useWorkspace(), {
      wrapper: createWrapper(),
    });
    await waitFor(() => expect(result.current.hasWorkspace).toBe(true));

    act(() => window.dispatchEvent(new Event(SESSION_EXPIRED_EVENT)));

    expect(result.current.hasWorkspace).toBe(false);
    expect(result.current.meta).toBeNull();
  });
});
