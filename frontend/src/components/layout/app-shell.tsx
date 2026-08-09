"use client";

import * as Dialog from "@radix-ui/react-dialog";
import {
  RefreshCw,
  Database,
  GitBranch,
  House,
  ListFilter,
  ListPlus,
  LayoutDashboard,
  X,
  Menu,
} from "lucide-react";
import Link from "next/link";
import { usePathname } from "next/navigation";
import type { ReactNode } from "react";

import { Logo } from "@/components/layout/logo";
import { cn } from "@/lib/utils";

const navItems = [
  { href: "/overview", label: "Overview", icon: LayoutDashboard },
  { href: "/imports", label: "Imports", icon: ListPlus },
  { href: "/runs", label: "Runs", icon: RefreshCw },
  { href: "/exceptions", label: "Workbench", icon: ListFilter },
  { href: "/dataset", label: "Dataset", icon: Database },
  { href: "/architecture", label: "Architecture", icon: GitBranch },
];

function NavLinks({ onNavigate }: { onNavigate?: () => void }) {
  const pathname = usePathname();
  return (
    <nav
      aria-label="Workspace navigation"
      className="flex flex-col gap-1 lg:flex-row"
    >
      {navItems.map(({ href, label, icon: Icon }) => {
        const active = pathname === href || pathname.startsWith(`${href}/`);
        return (
          <Link
            key={href}
            href={href}
            {...(onNavigate ? { onClick: onNavigate } : {})}
            className={cn(
              "inline-flex min-h-11 items-center gap-2 rounded-full px-4 text-sm font-bold transition-colors focus-visible:ring-3 focus-visible:ring-[var(--focus)] focus-visible:outline-none",
              active
                ? "bg-[var(--ink)] text-white"
                : "text-[var(--muted-strong)] hover:bg-white hover:text-[var(--ink)]",
            )}
            aria-current={active ? "page" : undefined}
          >
            <Icon size={17} strokeWidth={active ? 2.5 : 2} aria-hidden="true" />
            {label}
          </Link>
        );
      })}
    </nav>
  );
}

export function AppShell({ children }: { children: ReactNode }) {
  return (
    <div className="min-h-screen bg-[var(--environment)] px-0 py-0 sm:px-3 sm:py-3 xl:px-6 xl:py-6">
      <div className="mx-auto min-h-[calc(100vh-3rem)] max-w-[1540px] overflow-hidden bg-[var(--canvas)] sm:rounded-[2rem] sm:border sm:border-[var(--border-strong)] sm:shadow-[var(--shadow-shell)]">
        <header className="sticky top-0 z-40 border-b border-[var(--border)] bg-[var(--canvas)] px-4 py-3 sm:px-6 xl:px-8">
          <div className="flex items-center justify-between gap-4">
            <Logo />
            <div className="hidden lg:block">
              <NavLinks />
            </div>
            <div className="flex items-center gap-2">
              <Link
                href="/scenarios"
                className="hidden min-h-11 items-center gap-2 rounded-full border border-[var(--border-strong)] bg-white px-4 text-sm font-bold focus-visible:ring-3 focus-visible:ring-[var(--focus)] focus-visible:outline-none sm:inline-flex"
              >
                <House size={17} aria-hidden="true" />
                Scenarios
              </Link>
              <Dialog.Root>
                <Dialog.Trigger asChild>
                  <button
                    className="grid size-11 place-items-center rounded-full border border-[var(--border-strong)] bg-white focus-visible:ring-3 focus-visible:ring-[var(--focus)] focus-visible:outline-none lg:hidden"
                    aria-label="Open navigation"
                  >
                    <Menu size={22} aria-hidden="true" />
                  </button>
                </Dialog.Trigger>
                <Dialog.Portal>
                  <Dialog.Overlay className="data-[state=open]:animate-in fixed inset-0 z-50 bg-black/45 motion-reduce:animate-none" />
                  <Dialog.Content className="fixed inset-y-0 right-0 z-50 flex w-[min(88vw,380px)] flex-col border-l border-white/10 bg-[var(--ink)] p-5 text-white shadow-2xl outline-none">
                    <div className="mb-8 flex items-center justify-between">
                      <Logo light />
                      <Dialog.Close
                        className="grid size-11 place-items-center rounded-full border border-white/20 focus-visible:ring-3 focus-visible:ring-[var(--focus)]"
                        aria-label="Close navigation"
                      >
                        <X size={22} aria-hidden="true" />
                      </Dialog.Close>
                    </div>
                    <div className="[&_a]:text-white [&_a:hover]:bg-white/10 [&_a[aria-current=page]]:bg-white [&_a[aria-current=page]]:text-[var(--ink)]">
                      <NavLinks />
                    </div>
                    <Link
                      href="/scenarios"
                      className="mt-auto inline-flex min-h-11 items-center justify-center rounded-full bg-[var(--coral)] px-5 font-bold text-[var(--ink)]"
                    >
                      Change scenario
                    </Link>
                  </Dialog.Content>
                </Dialog.Portal>
              </Dialog.Root>
            </div>
          </div>
        </header>
        <main
          id="main-content"
          className="px-4 py-7 sm:px-6 sm:py-8 xl:px-8 xl:py-10"
        >
          {children}
        </main>
      </div>
    </div>
  );
}
