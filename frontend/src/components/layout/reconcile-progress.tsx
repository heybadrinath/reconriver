import { Check } from "lucide-react";
import Link from "next/link";

import { cn } from "@/lib/utils";

const steps = [
  { number: 1, label: "Choose data", href: "/scenarios" },
  { number: 2, label: "Review sources", href: "/imports" },
  { number: 3, label: "Reconcile", href: "/runs" },
] as const;

export function ReconcileProgress({
  current,
  completedThrough = current - 1,
}: {
  current: 1 | 2 | 3;
  completedThrough?: number;
}) {
  return (
    <nav
      aria-label="Reconciliation progress"
      className="mb-3 rounded-[var(--radius-card)] border border-[var(--border)] bg-[var(--surface)] p-2 shadow-[var(--shadow-card)]"
    >
      <ol className="grid grid-cols-3 gap-1.5">
        {steps.map((step) => {
          const active = step.number === current;
          const complete = step.number <= completedThrough;
          const status = active
            ? "Current"
            : complete
              ? "Completed"
              : "Not completed";

          return (
            <li key={step.href}>
              <Link
                href={step.href}
                aria-current={active ? "step" : undefined}
                aria-label={`${status}: ${step.label}`}
                className={cn(
                  "flex min-h-12 items-center gap-2 rounded-2xl px-2.5 text-xs font-bold transition-colors focus-visible:ring-3 focus-visible:ring-[var(--focus)] focus-visible:outline-none sm:px-4 sm:text-sm",
                  active
                    ? "bg-[var(--ink)] text-white"
                    : "text-[var(--muted-strong)] hover:bg-[var(--surface-muted)] hover:text-[var(--ink)]",
                )}
              >
                <span
                  className={cn(
                    "grid size-7 shrink-0 place-items-center rounded-full border font-mono text-[0.65rem]",
                    active
                      ? "border-white/25 bg-white/10"
                      : complete
                        ? "border-[#77b89a] bg-[var(--green-soft)] text-[var(--ink)]"
                        : "border-[var(--border-strong)] bg-white",
                  )}
                >
                  {complete && !active ? (
                    <Check size={14} aria-hidden="true" />
                  ) : (
                    step.number
                  )}
                </span>
                <span className="min-w-0 leading-tight sm:truncate">
                  {step.label}
                </span>
              </Link>
            </li>
          );
        })}
      </ol>
    </nav>
  );
}
