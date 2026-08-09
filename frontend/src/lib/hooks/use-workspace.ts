"use client";

import { useMutation } from "@tanstack/react-query";
import { useEffect, useState } from "react";

import {
  api,
  clearSession,
  getSessionToken,
  SESSION_EXPIRED_EVENT,
  SESSION_META_KEY,
  storeSession,
} from "@/lib/api/client";

interface SessionMeta {
  workspaceId: string;
  expiresAt: string;
  warning: string;
}

function readSessionMeta(): SessionMeta | null {
  if (typeof window === "undefined") return null;
  const raw = window.sessionStorage.getItem(SESSION_META_KEY);
  if (!raw) return null;
  try {
    return JSON.parse(raw) as SessionMeta;
  } catch {
    return null;
  }
}

export function useWorkspace() {
  const [ready, setReady] = useState(false);
  const [token, setToken] = useState<string | null>(null);
  const [meta, setMeta] = useState<SessionMeta | null>(null);

  useEffect(() => {
    const handleExpiry = () => {
      setToken(null);
      setMeta(null);
    };
    window.addEventListener(SESSION_EXPIRED_EVENT, handleExpiry);
    const timer = window.setTimeout(() => {
      const currentToken = getSessionToken();
      const currentMeta = readSessionMeta();
      if (
        currentMeta &&
        new Date(currentMeta.expiresAt).getTime() <= Date.now()
      ) {
        clearSession();
        setToken(null);
        setMeta(null);
      } else {
        setToken(currentToken);
        setMeta(currentMeta);
      }
      setReady(true);
    }, 0);
    return () => {
      window.clearTimeout(timer);
      window.removeEventListener(SESSION_EXPIRED_EVENT, handleExpiry);
    };
  }, []);

  useEffect(() => {
    if (!meta) return;
    const remaining = new Date(meta.expiresAt).getTime() - Date.now();
    if (!Number.isFinite(remaining)) return;
    const timer = window.setTimeout(
      () => {
        clearSession();
        setToken(null);
        setMeta(null);
      },
      Math.max(0, Math.min(remaining, 2_147_483_647)),
    );
    return () => window.clearTimeout(timer);
  }, [meta]);

  const start = useMutation({
    mutationFn: api.createSession,
    onSuccess(session) {
      storeSession(session);
      setToken(session.token);
      setMeta({
        workspaceId: session.workspaceId,
        expiresAt: session.expiresAt,
        warning: session.warning,
      });
    },
  });

  return {
    ready,
    hasWorkspace: Boolean(token),
    meta,
    start,
    clear() {
      clearSession();
      setToken(null);
      setMeta(null);
    },
  };
}
