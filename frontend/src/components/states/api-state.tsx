"use client";

import { RefreshCw, Database, Hourglass, TriangleAlert } from "lucide-react";

import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { classifyApiError } from "@/lib/api/client";

export function ApiErrorState({
  error,
  onRetry,
}: {
  error: unknown;
  onRetry?: () => void;
}) {
  const state = classifyApiError(error);
  return (
    <Card className="border-[#e9afa5] bg-[var(--coral-soft)]">
      <CardContent className="flex flex-col items-start gap-4 sm:flex-row sm:items-center">
        <span className="grid size-11 shrink-0 place-items-center rounded-full bg-white">
          <TriangleAlert size={22} aria-hidden="true" />
        </span>
        <div className="min-w-0 flex-1">
          <h2 className="font-display text-2xl leading-none font-bold uppercase">
            {state.title}
          </h2>
          <p className="mt-1 text-sm leading-6 text-[var(--muted-strong)]">
            {state.message}
          </p>
        </div>
        {onRetry ? (
          <Button variant="secondary" onClick={onRetry}>
            <RefreshCw size={18} aria-hidden="true" />
            {state.action}
          </Button>
        ) : null}
      </CardContent>
    </Card>
  );
}

export function ColdStartState() {
  return (
    <div
      className="flex items-start gap-3 rounded-2xl border border-[#d6bd63] bg-[var(--yellow-soft)] p-4"
      role="status"
    >
      <Hourglass
        className="mt-0.5 shrink-0"
        size={20}

        aria-hidden="true"
      />
      <div>
        <p className="font-bold">The backend is waking up.</p>
        <p className="text-sm text-[var(--muted-strong)]">
          This can take up to a minute on the free demo. Keep this page open.
        </p>
      </div>
    </div>
  );
}

export function EmptyState({
  title,
  description,
  action,
}: {
  title: string;
  description: string;
  action?: React.ReactNode;
}) {
  return (
    <Card className="border-dashed bg-[var(--surface-muted)]">
      <CardContent className="grid min-h-64 place-items-center text-center">
        <div className="max-w-md">
          <span className="mx-auto mb-4 grid size-12 place-items-center rounded-full bg-white">
            <Database size={24} aria-hidden="true" />
          </span>
          <h2 className="font-display text-3xl leading-none font-bold uppercase">
            {title}
          </h2>
          <p className="mt-2 text-sm leading-6 text-[var(--muted)]">
            {description}
          </p>
          {action ? (
            <div className="mt-5 flex justify-center">{action}</div>
          ) : null}
        </div>
      </CardContent>
    </Card>
  );
}
