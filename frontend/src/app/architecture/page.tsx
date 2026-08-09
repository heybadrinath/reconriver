import {
  ArrowDown,
  ArrowRight,
  Braces,
  CircleCheck,
  Database,
  GitBranch,
  Server,
  KeyRound,
  Repeat,
  ShieldCheck,
  Layers3,
} from "lucide-react";
import Link from "next/link";

import { AppShell } from "@/components/layout/app-shell";
import { PageIntro } from "@/components/layout/page-intro";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";

const modules = [
  [
    "Workspace access",
    "Issues short-lived tokens and isolates every import, run and audit event.",
  ],
  [
    "Import",
    "Validates CSV structure, calculates checksums and quarantines malformed rows.",
  ],
  [
    "Reconciliation",
    "Applies deterministic payment and settlement rules using BigDecimal.",
  ],
  [
    "Investigation",
    "Owns exception state transitions, operator notes and audit events.",
  ],
];

export default function ArchitecturePage() {
  return (
    <AppShell>
      <PageIntro
        eyebrow="Platform design"
        title="System architecture"
        description="ReconRiver uses a modular monolith so imports, reconciliation, exception decisions and audit events remain transactionally consistent behind explicit domain boundaries."
        actions={
          <Button asChild>
            <a
              href={`${process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080"}/docs`}
              target="_blank"
              rel="noreferrer"
            >
              Open API docs <ArrowRight size={18} aria-hidden="true" />
            </a>
          </Button>
        }
      />

      <Card className="mb-3 bg-[var(--ink)] text-white">
        <CardContent>
          <p className="utility-label text-white/45">
            Request to durable outcome
          </p>
          <div className="mt-7 grid gap-2 lg:grid-cols-[1fr_auto_1fr_auto_1fr_auto_1fr] lg:items-center">
            {[
              {
                title: "Next.js",
                detail: "Typed UI + Bearer token",
                icon: Braces,
              },
              {
                title: "Spring Boot",
                detail: "Security + REST orchestration",
                icon: Layers3,
              },
              {
                title: "Spring Batch",
                detail: "Chunked, restartable rules",
                icon: Repeat,
              },
              {
                title: "PostgreSQL",
                detail: "Constraints + audit history",
                icon: Database,
              },
            ].map(({ title, detail, icon: Icon }, index) => (
              <div key={title} className="contents">
                <div className="rounded-2xl border border-white/10 bg-white/5 p-5">
                  <Icon size={25} aria-hidden="true" />
                  <h2 className="mt-8 font-display text-3xl font-bold uppercase">
                    {title}
                  </h2>
                  <p className="mt-2 text-xs leading-5 text-white/50">
                    {detail}
                  </p>
                </div>
                {index < 3 ? (
                  <div className="grid place-items-center text-[var(--coral)]">
                    <ArrowDown
                      className="lg:hidden"
                      size={20}
                      aria-hidden="true"
                    />
                    <ArrowRight
                      className="hidden lg:block"
                      size={20}
                      aria-hidden="true"
                    />
                  </div>
                ) : null}
              </div>
            ))}
          </div>
        </CardContent>
      </Card>

      <div className="data-grid mb-3">
        <Card className="col-span-12 overflow-hidden border-t-4 border-t-[var(--violet)] lg:col-span-7">
          <CardContent>
            <GitBranch size={28} aria-hidden="true" />
            <p className="utility-label mt-8 text-[var(--muted)]">
              Spring Modulith
            </p>
            <h2 className="mt-2 font-display text-5xl leading-[0.85] font-extrabold uppercase sm:text-7xl">
              Boundaries without a network.
            </h2>
            <p className="mt-5 max-w-2xl text-sm leading-7 text-[var(--muted-strong)]">
              Controllers call application services, never repositories. Modules
              expose purposeful APIs and publish domain events after committed
              state changes. Modulith verification and architecture tests guard
              those boundaries.
            </p>
          </CardContent>
        </Card>
        <Card className="col-span-12 lg:col-span-5">
          <CardContent>
            <p className="utility-label text-[var(--muted)]">Domain modules</p>
            <div className="mt-5 divide-y divide-[var(--border)]">
              {modules.map(([title, detail]) => (
                <div key={title} className="py-4">
                  <h3 className="font-display text-2xl font-bold uppercase">
                    {title}
                  </h3>
                  <p className="mt-1 text-xs leading-5 text-[var(--muted)]">
                    {detail}
                  </p>
                </div>
              ))}
            </div>
          </CardContent>
        </Card>
      </div>

      <div className="grid gap-3 md:grid-cols-2 xl:grid-cols-3">
        {[
          {
            icon: Repeat,
            title: "Restartable batches",
            text: "Chunk commits persist Spring Batch checkpoints. A failed execution resumes from durable progress without repeating completed work.",
            tone: "border-t-[var(--yellow)]",
          },
          {
            icon: Braces,
            title: "BigDecimal money",
            text: "Amounts, fees and differences use explicit scale and rounding. Binary floating point never decides a financial match.",
            tone: "border-t-[var(--green)]",
          },
          {
            icon: KeyRound,
            title: "Idempotent imports",
            text: "Workspace, source type and SHA-256 checksum form a duplicate boundary before any row is imported twice.",
            tone: "border-t-[var(--coral)]",
          },
          {
            icon: Server,
            title: "Database locking",
            text: "PostgreSQL constraints and explicit locking protect competing workers and exception state transitions.",
            tone: "border-t-[var(--ink)]",
          },
          {
            icon: ShieldCheck,
            title: "Append-only audit",
            text: "Resolution and reopen actions append actor, time and details. Original reconciliation facts are never overwritten.",
            tone: "border-t-[var(--violet)]",
          },
          {
            icon: CircleCheck,
            title: "Testcontainers",
            text: "The integration-test design covers Flyway, constraints, CSV import, complete runs, restarts and workspace isolation against PostgreSQL containers.",
            tone: "border-t-[var(--green)]",
          },
        ].map(({ icon: Icon, title, text, tone }) => (
          <Card key={title} className={`border-t-4 ${tone}`}>
            <CardContent className="flex min-h-64 flex-col justify-between">
              <Icon size={27} aria-hidden="true" />
              <div className="mt-12">
                <h2 className="font-display text-3xl font-bold uppercase">
                  {title}
                </h2>
                <p className="mt-3 text-sm leading-6 text-[var(--muted-strong)]">
                  {text}
                </p>
              </div>
            </CardContent>
          </Card>
        ))}
      </div>

      <Card className="mt-3">
        <CardContent>
          <p className="utility-label text-[var(--muted)]">
            Spring Batch lifecycle
          </p>
          <ol className="mt-6 grid gap-3 md:grid-cols-4">
            {[
              [
                "Validate",
                "Confirm three source imports and workspace ownership.",
              ],
              [
                "Normalize",
                "Read immutable source records into a common money model.",
              ],
              [
                "Reconcile",
                "Apply payment rules, then aggregate settlement batches.",
              ],
              [
                "Publish",
                "Persist explainable results and emit a completion event.",
              ],
            ].map(([title, text], index) => (
              <li
                key={title}
                className="rounded-2xl border border-[var(--border)] bg-[var(--surface-muted)] p-4"
              >
                <span className="font-mono text-xs text-[var(--coral-strong)]">
                  0{index + 1}
                </span>
                <h2 className="mt-8 font-display text-3xl font-bold uppercase">
                  {title}
                </h2>
                <p className="mt-2 text-xs leading-5 text-[var(--muted)]">
                  {text}
                </p>
              </li>
            ))}
          </ol>
        </CardContent>
      </Card>

      <div className="mt-3 grid gap-3 lg:grid-cols-2">
        <Card className="overflow-hidden border-t-4 border-t-[var(--green)]">
          <CardContent>
            <p className="utility-label text-[var(--muted)]">
              Deployment topology
            </p>
            <h2 className="mt-3 font-display text-4xl font-bold uppercase">
              Vercel → Render → Neon
            </h2>
            <p className="mt-4 text-sm leading-7 text-[var(--muted-strong)]">
              Next.js is deployed separately from the containerized Java API.
              Exact CORS origins, environment-managed secrets, readiness probes
              and expiring workspaces define the hosted runtime boundary.
              PostgreSQL preserves workspace and batch state independently of
              the application container.
            </p>
          </CardContent>
        </Card>
        <Card className="overflow-hidden border-t-4 border-t-[var(--coral)]">
          <CardContent>
            <p className="utility-label text-[var(--muted)]">
              Data and access boundary
            </p>
            <h2 className="mt-3 font-display text-4xl font-bold uppercase">
              Synthetic data, isolated workspaces.
            </h2>
            <p className="mt-4 text-sm leading-7 text-[var(--muted-strong)]">
              Workspace tokens expire after 24 hours. Upload limits, rate
              controls and workspace-scoped queries constrain access, while
              append-only audit entries preserve operator actions. The hosted
              workspace accepts synthetic data only.
            </p>
          </CardContent>
        </Card>
      </div>

      <Card className="mt-3 bg-[var(--ink)] text-white">
        <CardContent className="flex flex-col justify-between gap-5 sm:flex-row sm:items-center">
          <div>
            <p className="utility-label text-white/45">See it operate</p>
            <h2 className="mt-2 font-display text-4xl font-bold uppercase">
              Follow a scenario through the complete system.
            </h2>
          </div>
          <Button asChild variant="coral">
            <Link href="/scenarios">
              Open a workspace <ArrowRight size={18} aria-hidden="true" />
            </Link>
          </Button>
        </CardContent>
      </Card>
    </AppShell>
  );
}
