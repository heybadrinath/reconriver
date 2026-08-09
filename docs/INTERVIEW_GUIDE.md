# ReconRiver interview guide

## The 60-second explanation

ReconRiver is a Java-first payment reconciliation workbench. A finance operations analyst loads three synthetic exports: the internal ledger, processor activity, and bank settlements. Spring Boot validates each CSV and stages its raw rows; Spring Batch then normalizes and imports those staged rows in restartable chunk jobs. A second Batch job applies deterministic rules in two stages: ledger to processor, followed by processor batches to bank credits. Every result includes the exact rule, explanation, related records, money, timestamps, and resolution state. PostgreSQL constraints protect canonical file, active-run, work-item, and result identities; a Spring Modulith test verifies the declared module dependency graph; and operator actions create append-only audit events. The Next.js app is deliberately only the interface: it polls API job progress and renders the API’s outcomes as a Reconciliation River, investigation workbench, traces, and reports.

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
- The Testcontainers integration suite contains journeys for Flyway, constraints, CSV imports, full reconciliation, restart, competing runs, audit, workspace isolation, cleanup, and REST behavior against PostgreSQL. Cite that coverage as verified only after the complete Maven gate passes.
- The Modulith test calls `ApplicationModules.verify()` to detect cycles and illegal cross-module dependencies. It is not a custom rule that forbids every same-module controller-to-repository call.
- Frontend tests cover components, API states, filters, uploads, resolution, errors, and accessibility.
- Playwright is configured to exercise the real anonymous journey from scenario load through resolve, reopen, audit, and report download without stubbing API results. Treat it as deployment evidence only after it passes against the stable deployed API.

## Free deployment compromises

- The backend may sleep and take about a minute to wake.
- A 512 MB/0.1 CPU free instance requires streaming, a small connection pool, conservative chunks, and a bounded heap.
- Neon Free storage and egress make short retention essential.
- Public imports are limited to 25,000 rows; the 100,000-record scenario is local only.
- Rate limiting is suitable for one public demo instance, not a globally distributed production control.
- No real identity, organization, role, retention, reconciliation approval chain, or compliance program is claimed.

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
