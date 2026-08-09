import { Route } from "lucide-react";
import Link from "next/link";

import { cn } from "@/lib/utils";

export function Logo({
  light = false,
  compact = false,
}: {
  light?: boolean;
  compact?: boolean;
}) {
  return (
    <Link
      href="/"
      className={cn(
        "inline-flex min-h-11 items-center gap-2 rounded-full focus-visible:ring-3 focus-visible:ring-[var(--focus)] focus-visible:outline-none",
        light ? "text-white" : "text-[var(--ink)]",
      )}
      aria-label="ReconRiver home"
    >
      <span
        className={cn(
          "grid size-10 place-items-center rounded-full",
          light ? "bg-white text-[var(--ink)]" : "bg-[var(--ink)] text-white",
        )}
      >
        <Route size={22} aria-hidden="true" />
      </span>
      {!compact ? (
        <span className="font-display text-2xl leading-none font-extrabold tracking-[-0.02em] uppercase">
          Recon<span className="text-[var(--coral-strong)]">River</span>
        </span>
      ) : null}
    </Link>
  );
}
