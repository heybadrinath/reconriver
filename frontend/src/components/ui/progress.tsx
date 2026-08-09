"use client";

import * as ProgressPrimitive from "@radix-ui/react-progress";

import { cn } from "@/lib/utils";

export function Progress({
  value,
  className,
}: {
  value: number;
  className?: string;
}) {
  const normalized = Math.max(0, Math.min(100, value));
  return (
    <ProgressPrimitive.Root
      className={cn(
        "relative h-3 overflow-hidden rounded-full border border-[var(--border)] bg-[var(--surface-muted)]",
        className,
      )}
      value={normalized}
      aria-label="Reconciliation progress"
    >
      <ProgressPrimitive.Indicator
        className="h-full bg-[var(--green)] transition-transform duration-300 motion-reduce:transition-none"
        style={{ transform: `translateX(-${100 - normalized}%)` }}
      />
    </ProgressPrimitive.Root>
  );
}
