# ReconRiver interview guide

## The 60-second explanation

ReconRiver is a Java-first payment reconciliation workbench. A finance operations analyst loads three synthetic exports: the internal ledger, processor activity, and bank settlements. Spring Boot validates each CSV and stages its raw rows; Spring Batch then normalizes and imports those staged rows in restartable chunk jobs. A second Batch job applies deterministic rules in two stages: ledger to processor, followed by processor batches to bank credits. Every result includes the exact rule, explanation, related records, money, timestamps, and resolution state. PostgreSQL constraints protect canonical file, active-run, work-item, and result identities; a Spring Modulith test verifies the declared module dependency graph; and operator actions create append-only audit events. The Next.js app is deliberately only the interface: it polls API job progress and renders the API’s outcomes as a Reconciliation River, investigation workbench, traces, and reports.

The useful distinction is that this is a working, publicly deployed portfolio system with deterministic synthetic fixtures, not a claim that a free anonymous demo is ready to process real financial data.

## Verified demonstration evidence

The public deployment is available at [reconriver.vercel.app](https://reconriver.vercel.app), backed by the [Render API](https://reconriver-api.onrender.com/docs), Neon PostgreSQL, and [16 Hugging Face Dataset Viewer configurations](https://huggingface.co/datasets/heybadrinath/reconriver-synthetic-reconciliation). The following runs were observed on the public Render Free service on 2026-08-09:

| Scenario                        | Total results | Matched | Exceptions | Observed elapsed time |
| ------------------------------- | ------------: | ------: | ---------: | --------------------: |
| Mixed exceptions                |         1,244 |   1,007 |        237 |                19.9 s |
| Month-end close                 |        11,539 |  11,239 |        300 |          Not recorded |
| Controlled failure and recovery |        11,369 |  10,813 |        556 |              264.65 s |

The month-end and recovery scenarios each begin with 10,000 canonical payments. Result totals include order-level and settlement-level work, which is why they exceed 10,000. The first Render deployment reached readiness in 207 seconds; that measures initial deployment startup, not an idle-service cold wake. Do not turn these observations into requests-per-second, production-capacity, or high-availability claims.

The production import-boundary check accepted a 25,000-row synthetic internal-ledger CSV, imported 25,000/25,000 rows, quarantined none, and completed in 260 seconds. During a separate rolling service replacement, the new Spring process started in 210.494 seconds while the existing public service remained available. The same workspace still held its 1,244 results and resolve/reopen audit entries afterward. This proves that those records were durable in Neon across that replacement; one no-downtime observation is not an availability guarantee.

## End-to-end data flow

1. `POST /api/v1/demo/sessions` creates a workspace and returns a raw bearer token once. Only a keyed hash is stored.
2. The Java generator starts with canonical synthetic payments and derives ledger, processor, bank, ground-truth, and manifest files from one seed and fixed start date.
3. The upload request checks the file name, media type, size, headers, row width, row count, and SHA-256 checksum while staging raw rows in PostgreSQL.
4. Spring Batch reads the staged rows, validates source fields, persists valid records, and quarantines invalid rows rather than silently discarding them. A byte-identical attempt against a finished import is recorded and skipped; an active attempt reuses the canonical import, while a failed one is requeued. Repeated source identifiers remain available for deterministic duplicate outcomes.
5. A partial unique database constraint guards the workspace against competing active runs; a concurrent start loses that race and returns a conflict.
6. Stage one compares the ledger with processor events. Stage two aggregates processor net amounts by settlement batch and compares each batch with a bank credit.
7. The API returns stored results and genuine job-repository progress. The frontend does not recalculate match status.
8. Resolve and reopen operations lock the selected result row, validate its current resolution state, write the transition, and append an audit event in one application transaction.

## Why Java 21 and Spring Boot

- Java records, enums, and a strong type system make rule inputs and outcomes explicit.
- `BigDecimal` avoids binary floating-point errors and supports declared scale and rounding.
- Spring Batch provides a durable job repository, chunk transactions, execution metadata, and restart semantics instead of a hand-built loop. Row quarantine is explicit application behavior rather than a generic skip policy.
- Spring Data JPA owns the workspace entity lifecycle; `JdbcClient` handles the batch-oriented domain persistence. Flyway migrations and PostgreSQL constraints remain the schema authority.
- Spring Security provides one filter chain for anonymous bearer authentication, CORS, rate limiting, and secure headers. The ingestion service separately enforces upload rules.
- Actuator exposes health and readiness, while Micrometer records lifecycle-event signals. The progress API reads genuine Spring Batch metadata.
- Testcontainers supplies real PostgreSQL behavior to the integration suite rather than substituting an in-memory database.

## Why a modular monolith

Independent services would make this portfolio larger but not better. Imports, reconciliation output, resolutions, and audit history need strong transactional relationships. One deployment is also appropriate for free infrastructure. Spring Modulith still makes boundaries visible and testable, so extracting a real hotspot later is possible without pretending the MVP already needs distributed systems.

The interview point is that module boundaries and service boundaries solve different problems. ReconRiver chooses module boundaries first and earns network boundaries later.

## Batch restart and idempotency

The job repository stores job instances and step executions in PostgreSQL. Job parameters identify the workspace and import or reconciliation run. Chunk commits limit rollback work. A controlled failure is persisted once on the run, while durable work-item `processed` flags and idempotent result writes identify the first unfinished work on restart. The implementation does not claim a custom CSV cursor in Spring Batch `ExecutionContext`.

Idempotency is enforced at several layers:

- workspace + source type + checksum identifies one canonical import; a byte-identical attempt against a finished import is stored as `DUPLICATE` and points to it, an active attempt returns the existing import, and a failed import is requeued;
- repeated source identifiers are retained deliberately so the reconciliation policy can classify them as duplicate outcomes;
- run + stable work identity prevents duplicate work and output on restart;
- a partial unique constraint prevents two active runs for the same workspace;
- application checks return useful conflicts, while database constraints protect races.

## Money and fee policy

CSV decimal strings become `BigDecimal` during row normalization. PostgreSQL stores monetary values as `NUMERIC(19,4)`, and the reconciliation engine normalizes comparisons to four decimal places with `HALF_EVEN`. The configured processor fee is rounded at currency scale with `HALF_UP` and then normalized for storage; the Java generator also emits two-decimal currency values with `HALF_UP`. `double` and `float` are not part of the money path.

The generator checks `net_amount = gross_amount - fee_amount`, pre-injection settlement totals, exception counts, IDs, manifest summaries, and SHA-256 checksums. That makes the dataset executable evidence rather than illustrative sample data.

## Rule precedence and explainability

Rules are ordered so one record does not receive a convenient but misleading result. Invalid and duplicate conditions are identified before exact matching. Exact merchant-order matching then requires currency and amount agreement, followed by fee validation and refund linking. Ambiguous records remain investigation items. Settlement rules compare batch identity, expected net, currency, duplicates, and booking windows.

Each persisted result keeps the outcome, rule code, human explanation, source IDs, amounts, difference, currency, timestamps, and resolution status. A recruiter can open the transaction trace and see why the engine reached that result.

## Concurrency and transactions

The run-start use case uses a database guard rather than an in-memory flag. That matters even on one free instance because duplicate HTTP requests and job restarts can still race. Result writes are chunk-transactional and protected by uniqueness. Resolve and reopen select the result `FOR UPDATE`, so concurrent operator transitions serialize and the second transition is checked against the committed state.

Required audit writes happen inside the same use-case transaction as the state change. Modulith events separate optional follow-on work without making the financial write depend on a broker.

## Workspace isolation and security

Every persisted business row references a workspace, and business queries explicitly include the workspace key. This application scoping is essential because cross-table ownership is not represented by composite workspace foreign keys. A random token never appears in the database; an HMAC hash does. Sessions expire after 24 hours and scheduled cleanup deletes their workspaces. The public demo accepts only CSV, caps requests at 10 MB and 25,000 rows, sanitizes filenames, limits row width, protects report cells from spreadsheet formulas, rate-limits requests, and repeatedly warns visitors to use synthetic data only.

This is a demonstration security model, not approval for real payment data.

## Spring Modulith events

Events document meaningful transitions such as import completed, reconciliation completed, and exception resolved. Durable JPA publication preserves publication state where listeners matter. A production system could externalize selected events to Kafka or another broker after defining consumers, delivery semantics, privacy rules, and replay behavior. Kafka is intentionally absent from the MVP.

## Test strategy

- Focused backend unit tests cover money normalization, fee calculation, CSV formula neutralization, production configuration, and rate limiting. The full rule matrix is compared with generated ground truth in the PostgreSQL integration journey rather than duplicated as isolated policy-unit fixtures.
- Generator tests prove seeds, checksums, injection counts, ground truth, clean scenarios, and mathematical invariants.
- The verified Java 21 Maven gate contains 32 tests: 16 API tests, including seven PostgreSQL Testcontainers journeys, and 16 generator tests. The integration journeys cover Flyway, constraints, CSV imports, full reconciliation, restart, competing runs, audit, workspace isolation, cleanup, and REST behavior against PostgreSQL. These are Maven Surefire tests; there is no separate Failsafe claim.
- The Modulith test calls `ApplicationModules.verify()` to detect cycles and illegal cross-module dependencies. It is not a custom rule that forbids every same-module controller-to-repository call.
- The verified frontend gate contains 35 Vitest unit/accessibility tests across 12 files, plus formatting, lint, type checking, OpenAPI-to-TypeScript drift, and the Next.js production build.
- Two Playwright projects—desktop Chromium and mobile Chromium—passed the real anonymous journey from scenario load through resolve, reopen, audit, report download, and axe checks without stubbing API results. Keep local browser proof distinct from the final production visual record.
- The executable JAR and repository-root Docker image passed runtime checks under a 320 MB heap. The image runs as UID `10001`. The contract gate compares the running Spring OpenAPI document to the committed document and regenerates TypeScript from that committed contract.

## Free deployment compromises

- The backend scales down after inactivity. Initial deployment readiness was 207 seconds and a rolling replacement started Spring in 210.494 seconds, but neither is a genuine idle-to-ready wake measurement.
- A 512 MB/0.1 CPU free instance requires streaming, a small connection pool, conservative chunks, and a bounded heap.
- Neon Free storage and egress make short retention essential.
- Public imports are limited to 25,000 rows; the 100,000-record scenario is local only.
- Rate limiting is suitable for one public demo instance, not a globally distributed production control.
- No real identity, organization, role, retention, reconciliation approval chain, or compliance program is claimed.

The live Render service was created through Render's official API using the checked-in `render.yaml` values. It is not Blueprint-linked. Automatic deployment is disabled so deployment can be gated on a tested commit; this is a deliberate operational deviation from the checked-in `checksPass` trigger.

## Five-minute interview walkthrough

1. **Set the business problem (30 seconds):** three systems can describe the same payment differently. Finance operations needs a repeatable answer and traceable exceptions, not just a totals dashboard.
2. **Start a workspace and load a scenario (45 seconds):** point out the synthetic-only warning and the three source files. Explain that staging, validation, checksums, and imports happen server-side.
3. **Run reconciliation (60 seconds):** show real Spring Batch progress. Explain the two-stage model and why `BigDecimal`, ordered rules, and stable work keys matter.
4. **Open the Reconciliation River and a trace (60 seconds):** use the visual path for orientation, then show that the API-backed table and named rule remain authoritative and accessible.
5. **Resolve and reopen an exception (45 seconds):** show row locking, state validation, and the append-only audit entries. Explain why the state transition and required audit write share one transaction.
6. **Discuss recovery and evidence (45 seconds):** cite the controlled five-chunk failure fixture, bodyless restart, exact expected results, and the measured free-instance run without extrapolating throughput.
7. **Close with limits (15 seconds):** this demonstrates architecture, correctness, deployment, and operational honesty; real financial use still requires identity, approvals, private storage, compliance, and resilience work.

## Resume-safe bullets

- Built and deployed a Java 21/Spring Boot 4 payment-reconciliation workbench with restartable Spring Batch imports and matching, deterministic `BigDecimal` rules, PostgreSQL constraints, exception resolution, audit history, and a Next.js 16 operator interface.
- Created four byte-reproducible synthetic scenario packs and exact ground truth; published 16 Hugging Face Dataset Viewer configurations with schema, manifests, archives, CC BY 4.0 documentation, and 32 verified SHA-256 entries.
- Verified 32 Maven tests, 35 frontend unit/accessibility tests, two real-API browser journeys, OpenAPI contract drift, non-root Docker execution, and a 25,000-row public import; observed a 10,000-payment recovery scenario complete with 11,369 results after controlled failure and restart on a free public deployment.

Do not replace “observed” with “benchmarked,” claim regulated-finance readiness, describe the free deployment as highly available, or imply the result counts are real customer volume.

## What would change for production

1. Add organization identity, least-privilege roles, approval workflows, and tamper-evident audit retention.
2. Put uploads in private object storage with malware scanning, encryption policy, and retention controls.
3. Split interactive API and batch workers only after measuring queueing or compute contention.
4. Add durable distributed rate limiting and workload admission control.
5. Add database high availability, backups, point-in-time restore, migration gates, and tested disaster recovery.
6. Externalize selected events only when real consumers and throughput justify a broker.
7. Add multi-currency policy, time-zone calendars, processor-specific fee versions, and controlled rule migration.
8. Add production observability, paging, SLOs, cost alerts, and privacy/security review.

## Honest weaknesses

- Merchant order ID is an intentionally simple primary match key; real processors often require normalized references and controlled operator suggestions.
- Demo data assumptions are not measurements of industry exception rates.
- Free-instance performance is not evidence of production scale.
- Workspace deletion is operational cleanup, not a regulated data-retention system.
- A modular monolith reduces distributed complexity but still needs disciplined ownership as it grows.
