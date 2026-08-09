import type { ReactNode } from "react";

export function PageIntro({
  eyebrow,
  title,
  description,
  actions,
}: {
  eyebrow: string;
  title: string;
  description: string;
  actions?: ReactNode;
}) {
  return (
    <div className="mb-7 flex flex-col justify-between gap-5 md:flex-row md:items-end">
      <div className="max-w-3xl">
        <p className="mb-2 font-mono text-[0.6875rem] font-semibold tracking-[0.14em] text-[var(--muted)] uppercase">
          {eyebrow}
        </p>
        <h1 className="font-display text-[clamp(2.6rem,6vw,5.5rem)] leading-[0.82] font-extrabold tracking-[-0.035em] uppercase">
          {title}
        </h1>
        <p className="mt-4 max-w-2xl text-sm leading-6 text-[var(--muted-strong)] sm:text-base sm:leading-7">
          {description}
        </p>
      </div>
      {actions ? (
        <div className="flex shrink-0 flex-wrap gap-2">{actions}</div>
      ) : null}
    </div>
  );
}
