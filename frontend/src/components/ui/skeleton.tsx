import { cn } from "@/lib/utils";

export function Skeleton({ className }: { className?: string }) {
  return (
    <span
      aria-hidden="true"
      className={cn(
        "block animate-pulse rounded-xl bg-[#e5eae8] motion-reduce:animate-none",
        className,
      )}
    />
  );
}

export function CardSkeleton() {
  return (
    <div className="rounded-[var(--radius-card)] border border-[var(--border)] bg-white p-6">
      <Skeleton className="mb-8 h-4 w-1/3" />
      <Skeleton className="mb-3 h-12 w-1/2" />
      <Skeleton className="h-4 w-2/3" />
    </div>
  );
}
