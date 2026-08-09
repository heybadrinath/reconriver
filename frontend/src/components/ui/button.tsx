import { Slot } from "@radix-ui/react-slot";
import { cva, type VariantProps } from "class-variance-authority";
import * as React from "react";

import { cn } from "@/lib/utils";

const buttonVariants = cva(
  "inline-flex min-h-11 items-center justify-center gap-2 rounded-full border text-sm font-bold transition-[background-color,color,border-color,transform] focus-visible:outline-none focus-visible:ring-3 focus-visible:ring-[var(--focus)] disabled:pointer-events-none disabled:opacity-45 motion-safe:hover:-translate-y-px",
  {
    variants: {
      variant: {
        primary:
          "border-[var(--ink)] bg-[var(--ink)] px-5 text-white hover:bg-[#292b2b]",
        secondary:
          "border-[var(--border-strong)] bg-[var(--surface)] px-5 text-[var(--ink)] hover:bg-white",
        coral:
          "border-[var(--coral-strong)] bg-[var(--coral)] px-5 text-[var(--ink)] hover:bg-[#ff6b52]",
        ghost:
          "border-transparent bg-transparent px-3 text-[var(--ink)] hover:bg-[var(--surface-muted)]",
        icon: "size-11 border-[var(--border)] bg-[var(--surface)] p-0 text-[var(--ink)] hover:bg-white",
        danger:
          "border-[var(--critical)] bg-[var(--critical)] px-5 text-white hover:bg-[#b72e27]",
      },
      size: {
        default: "h-11",
        sm: "min-h-9 px-4 text-xs",
        lg: "min-h-13 px-6 text-base",
      },
    },
    defaultVariants: { variant: "primary", size: "default" },
  },
);

export interface ButtonProps
  extends
    React.ButtonHTMLAttributes<HTMLButtonElement>,
    VariantProps<typeof buttonVariants> {
  asChild?: boolean;
}

export const Button = React.forwardRef<HTMLButtonElement, ButtonProps>(
  ({ className, variant, size, asChild = false, ...props }, ref) => {
    const Comp = asChild ? Slot : "button";
    return (
      <Comp
        className={cn(buttonVariants({ variant, size, className }))}
        ref={ref}
        {...props}
      />
    );
  },
);
Button.displayName = "Button";

export { buttonVariants };
