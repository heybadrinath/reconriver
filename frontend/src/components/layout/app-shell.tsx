"use client";

import * as Dialog from "@radix-ui/react-dialog";
import { BookOpen, Braces, CircleHelp, Menu, X } from "lucide-react";
import Link from "next/link";
import { usePathname } from "next/navigation";
import type { ReactNode } from "react";

import { Logo } from "@/components/layout/logo";
import { cn } from "@/lib/utils";

const navItems = [
  { href: "/overview", label: "Dashboard", paths: ["/overview"] },
  {
    href: "/scenarios",
    label: "Reconcile",
    paths: ["/scenarios", "/imports", "/runs"],
  },
  {
    href: "/exceptions",
    label: "Investigate",
    paths: ["/exceptions", "/transactions"],
  },
  { href: "/dataset", label: "Data", paths: ["/dataset"] },
];

function NavLinks({ onNavigate }: { onNavigate?: () => void }) {
  const pathname = usePathname();
  return (
    <nav
      aria-label="Workspace navigation"
      className="flex flex-col gap-1 lg:flex-row"
    >
      {navItems.map(({ href, label, paths }) => {
        const groupActive = paths.some(
          (path) => pathname === path || pathname.startsWith(`${path}/`),
        );
        const exactCurrent = pathname === href;
        return (
          <Link
            key={href}
            href={href}
            {...(onNavigate ? { onClick: onNavigate } : {})}
            data-active={groupActive ? "true" : undefined}
            className={cn(
              "inline-flex min-h-12 items-center rounded-full px-5 text-sm font-bold transition-colors focus-visible:ring-3 focus-visible:ring-[var(--focus)] focus-visible:outline-none",
              groupActive
                ? "bg-[var(--ink)] text-white"
                : "text-[var(--muted-strong)] hover:bg-white hover:text-[var(--ink)]",
            )}
            aria-current={exactCurrent ? "page" : undefined}
          >
            {label}
          </Link>
        );
      })}
    </nav>
  );
}

export function AppShell({ children }: { children: ReactNode }) {
  const pathname = usePathname();
  const apiUrl = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080";
  const systemCurrent = pathname === "/architecture";

  return (
    <div className="min-h-screen bg-[var(--environment)] sm:px-3 sm:py-3 xl:px-6 xl:py-6">
      <div className="app-canvas min-h-screen sm:min-h-[calc(100vh-3rem)] sm:rounded-[2rem] sm:border sm:border-[var(--border-strong)] sm:shadow-[var(--shadow-shell)]">
        <header className="sticky top-0 z-40 bg-[var(--canvas)] px-4 py-3 sm:px-6 xl:px-8">
          <div className="flex items-center justify-between gap-4">
            <Logo />
            <div className="hidden lg:block">
              <NavLinks />
            </div>
            <div className="flex items-center gap-2">
              <Link
                href="/architecture"
                aria-current={systemCurrent ? "page" : undefined}
                className={cn(
                  "hidden min-h-12 items-center gap-2 rounded-full border px-4 text-sm font-bold focus-visible:ring-3 focus-visible:ring-[var(--focus)] focus-visible:outline-none xl:inline-flex",
                  systemCurrent
                    ? "border-[var(--ink)] bg-[var(--ink)] text-white"
                    : "border-[var(--border)] bg-[var(--surface)] text-[var(--muted-strong)] hover:bg-white",
                )}
              >
                <CircleHelp size={18} aria-hidden="true" />
                System
              </Link>
              <a
                href={`${apiUrl}/docs`}
                target="_blank"
                rel="noreferrer"
                className="hidden size-12 place-items-center rounded-full border border-[var(--border)] bg-[var(--surface)] text-[var(--ink)] hover:bg-white focus-visible:ring-3 focus-visible:ring-[var(--focus)] focus-visible:outline-none sm:grid"
                aria-label="Open API documentation"
                title="API documentation"
              >
                <Braces size={19} aria-hidden="true" />
              </a>
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
                    <Dialog.Title className="sr-only">
                      Workspace navigation
                    </Dialog.Title>
                    <div className="mb-8 flex items-center justify-between">
                      <Logo light />
                      <Dialog.Close
                        className="grid size-11 place-items-center rounded-full border border-white/20 focus-visible:ring-3 focus-visible:ring-[var(--focus)]"
                        aria-label="Close navigation"
                      >
                        <X size={22} aria-hidden="true" />
                      </Dialog.Close>
                    </div>
                    <div className="[&_a]:text-white [&_a:hover]:bg-white/10 [&_a[data-active=true]]:bg-white [&_a[data-active=true]]:text-[var(--ink)]">
                      <NavLinks />
                    </div>
                    <div className="mt-auto grid gap-2 border-t border-white/10 pt-5">
                      <Link
                        href="/architecture"
                        aria-current={systemCurrent ? "page" : undefined}
                        className={cn(
                          "inline-flex min-h-12 items-center gap-3 rounded-full px-4 font-bold",
                          systemCurrent
                            ? "bg-white text-[var(--ink)]"
                            : "hover:bg-white/10",
                        )}
                      >
                        <CircleHelp size={19} aria-hidden="true" />
                        System architecture
                      </Link>
                      <a
                        href={`${apiUrl}/docs`}
                        target="_blank"
                        rel="noreferrer"
                        className="inline-flex min-h-12 items-center gap-3 rounded-full px-4 font-bold hover:bg-white/10"
                      >
                        <BookOpen size={19} aria-hidden="true" />
                        API documentation
                      </a>
                    </div>
                  </Dialog.Content>
                </Dialog.Portal>
              </Dialog.Root>
            </div>
          </div>
        </header>
        <main
          id="main-content"
          className="px-4 pt-5 pb-8 sm:px-6 sm:pt-6 sm:pb-10 xl:px-8"
        >
          {children}
        </main>
      </div>
    </div>
  );
}
