"use client";

import { ArrowRight, LoaderCircle } from "lucide-react";
import { useRouter } from "next/navigation";
import type { ReactNode } from "react";

import { ApiErrorState, EmptyState } from "@/components/states/api-state";
import { Button } from "@/components/ui/button";
import { AppShell } from "@/components/layout/app-shell";
import { useWorkspace } from "@/lib/hooks/use-workspace";

export function WorkspaceGate({ children }: { children: ReactNode }) {
  const workspace = useWorkspace();
  const router = useRouter();

  if (!workspace.ready) {
    return (
      <AppShell>
        <div className="grid min-h-[60vh] place-items-center" role="status">
          <LoaderCircle
            className="animate-spin motion-reduce:animate-none"
            size={32}
            aria-hidden="true"
          />
          <span className="sr-only">Checking workspace</span>
        </div>
      </AppShell>
    );
  }

  if (!workspace.hasWorkspace) {
    return (
      <AppShell>
        <EmptyState
          title="No active workspace"
          description="Start an isolated 24-hour workspace before importing synthetic payment data or running reconciliation."
          action={
            <Button
              onClick={() =>
                workspace.start.mutate(undefined, {
                  onSuccess: () => router.push("/scenarios"),
                })
              }
              disabled={workspace.start.isPending}
            >
              {workspace.start.isPending ? (
                <LoaderCircle
                  className="animate-spin"
                  size={18}
                  aria-hidden="true"
                />
              ) : (
                <ArrowRight size={18} aria-hidden="true" />
              )}
              Start workspace
            </Button>
          }
        />
        {workspace.start.error ? (
          <div className="mt-4">
            <ApiErrorState
              error={workspace.start.error}
              onRetry={() => workspace.start.mutate()}
            />
          </div>
        ) : null}
      </AppShell>
    );
  }

  return <AppShell>{children}</AppShell>;
}
