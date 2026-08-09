"use client";

import * as SelectPrimitive from "@radix-ui/react-select";
import { ChevronDown, Check } from "lucide-react";

import { cn } from "@/lib/utils";

export interface SelectOption {
  label: string;
  value: string;
}

export function Select({
  value,
  onValueChange,
  options,
  label,
  placeholder,
  className,
}: {
  value?: string;
  onValueChange: (value: string) => void;
  options: SelectOption[];
  label: string;
  placeholder?: string;
  className?: string;
}) {
  return (
    <SelectPrimitive.Root
      {...(value === undefined ? {} : { value })}
      onValueChange={onValueChange}
    >
      <SelectPrimitive.Trigger
        aria-label={label}
        className={cn(
          "inline-flex min-h-11 min-w-36 items-center justify-between gap-3 rounded-full border border-[var(--border-strong)] bg-[var(--surface)] px-4 text-sm font-bold outline-none focus-visible:ring-3 focus-visible:ring-[var(--focus)]",
          className,
        )}
      >
        <SelectPrimitive.Value placeholder={placeholder} />
        <SelectPrimitive.Icon>
          <ChevronDown size={16} aria-hidden="true" />
        </SelectPrimitive.Icon>
      </SelectPrimitive.Trigger>
      <SelectPrimitive.Portal>
        <SelectPrimitive.Content
          position="popper"
          sideOffset={6}
          className="z-50 min-w-[var(--radix-select-trigger-width)] overflow-hidden rounded-2xl border border-[var(--border-strong)] bg-white p-1 shadow-xl"
        >
          <SelectPrimitive.Viewport>
            {options.map((option) => (
              <SelectPrimitive.Item
                key={option.value}
                value={option.value}
                className="relative flex cursor-default items-center rounded-xl py-2.5 pr-8 pl-3 text-sm font-semibold outline-none select-none data-[highlighted]:bg-[var(--surface-muted)]"
              >
                <SelectPrimitive.ItemText>
                  {option.label}
                </SelectPrimitive.ItemText>
                <SelectPrimitive.ItemIndicator className="absolute right-2">
                  <Check size={16} aria-hidden="true" />
                </SelectPrimitive.ItemIndicator>
              </SelectPrimitive.Item>
            ))}
          </SelectPrimitive.Viewport>
        </SelectPrimitive.Content>
      </SelectPrimitive.Portal>
    </SelectPrimitive.Root>
  );
}
