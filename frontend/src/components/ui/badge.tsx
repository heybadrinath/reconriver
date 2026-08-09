import { CircleCheck, Clock, CircleAlert, XCircle } from "lucide-react";

import { cn, titleCase } from "@/lib/utils";

const semantic = {
  success: "border-[#20865d] bg-[var(--green-soft)] text-[#165f44]",
  warning: "border-[#9e7913] bg-[var(--yellow-soft)] text-[#69500b]",
  danger: "border-[#bc3828] bg-[var(--coral-soft)] text-[#8e291d]",
  violet: "border-[#704bbd] bg-[var(--violet-soft)] text-[#53368e]",
  neutral:
    "border-[var(--border-strong)] bg-[var(--surface-muted)] text-[var(--muted)]",
};

export function statusTone(value: string) {
  const normalized = value.toUpperCase();
  if (
    ["COMPLETED", "MATCHED", "RESOLVED", "IMPORTED", "CLEAN"].includes(
      normalized,
    )
  )
    return "success" as const;
  if (
    ["RUNNING", "STARTING", "PENDING", "LATE", "VALIDATING"].includes(
      normalized,
    )
  )
    return "warning" as const;
  if (
    ["FAILED", "OPEN", "MISMATCHED", "QUARANTINED", "ERROR"].includes(
      normalized,
    )
  )
    return "danger" as const;
  if (["REFUND", "REFUNDED", "REOPENED"].includes(normalized))
    return "violet" as const;
  return "neutral" as const;
}

export function StatusBadge({
  value,
  className,
}: {
  value: string;
  className?: string;
}) {
  const tone = statusTone(value);
  const Icon =
    tone === "success"
      ? CircleCheck
      : tone === "warning"
        ? Clock
        : tone === "danger"
          ? XCircle
          : tone === "violet"
            ? CircleAlert
            : CircleCheck;
  return (
    <span
      className={cn(
        "inline-flex w-fit items-center gap-1.5 rounded-full border px-2.5 py-1 text-[0.6875rem] font-extrabold tracking-[0.05em] uppercase",
        semantic[tone],
        className,
      )}
    >
      <Icon size={14} aria-hidden="true" />
      {titleCase(value)}
    </span>
  );
}
