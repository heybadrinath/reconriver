# ReconRiver web

Next.js App Router interface for the ReconRiver payment reconciliation and settlement workbench. The browser presents API results; the Spring Boot service owns import validation, monetary calculations, reconciliation rules, job state and audit history.

## Requirements

- Node.js 24 LTS
- pnpm 11
- ReconRiver API running locally or deployed

## Run locally

```bash
pnpm install
NEXT_PUBLIC_API_URL=http://localhost:8080 pnpm dev
```

Open `http://localhost:3000`. The public landing, dataset and architecture routes work without a demo token. Starting a workspace stores the short-lived Bearer token in `sessionStorage`; it is never persisted in a cookie or local storage.

## Environment

| Variable                  | Purpose                                     | Local default                       |
| ------------------------- | ------------------------------------------- | ----------------------------------- |
| `NEXT_PUBLIC_API_URL`     | Spring Boot API origin                      | `http://localhost:8080`             |
| `NEXT_PUBLIC_DATASET_URL` | Published Hugging Face dataset              | Hugging Face datasets index         |
| `OPENAPI_URL`             | Contract source for generation/drift checks | `http://localhost:8080/v3/api-docs` |
| `E2E_API_URL`             | Real API used by Playwright                 | unset; journey test skips           |

## Quality checks

```bash
pnpm format:check
pnpm lint
pnpm typecheck
pnpm test
pnpm build
```

The primary Playwright journey deliberately does not stub transactions, totals, progress or audit events:

```bash
E2E_API_URL=http://localhost:8080 pnpm test:e2e
```

It starts an anonymous session, loads the mixed-exceptions scenario, runs the real batch job, checks the returned total, resolves and reopens an exception, verifies audit history and downloads the report.

## OpenAPI contract

With the Spring API available:

```bash
pnpm api:refresh
pnpm api:check:live
```

`api:refresh` commits both the downloaded OpenAPI document and its generated TypeScript types. `api:check` is CI-safe: it regenerates types from the committed document without requiring a running backend. `api:check:live` additionally fails when the committed document differs from a running Spring API.

## Routes

- `/` — product landing and conceptual rule preview
- `/scenarios` — prepared dataset selection and file preview
- `/overview` — latest completed run metrics and Reconciliation River
- `/imports` — synthetic CSV intake and import history
- `/runs`, `/runs/[runId]` — genuine Spring Batch history and progress
- `/exceptions` — filterable investigation and resolution workbench
- `/transactions/[transactionId]` — source evidence and audit trace
- `/dataset` — generator, scenarios, licence and limitations
- `/architecture` — recruiter-oriented Java system tour

## Responsible use

Use synthetic demonstration data only. Do not upload real payment, customer, card or bank information. The public demo is not production financial infrastructure.
