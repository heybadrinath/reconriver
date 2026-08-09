# Render deployment

ReconRiver's Spring Boot API is live at [reconriver-api.onrender.com](https://reconriver-api.onrender.com/docs) as the `reconriver-api` [Render Free Web Service](https://render.com/docs/free) in Frankfurt.

The service was created through Render's official API using values equivalent to the repository's `render.yaml`; it is not linked to a Render Blueprint. The checked-in file remains the reviewable source for the intended Docker build, health check, free instance type, and non-secret runtime settings. One deliberate live-service difference is that automatic deployment is disabled, so a tested commit must be promoted manually; `render.yaml` retains `checksPass` for a future Blueprint-created service.

## Configured runtime shape

- Docker build context: repository root (`.`)
- Dockerfile: `backend/reconciliation-api/Dockerfile`
- Health check: `/actuator/health/readiness`
- Public Actuator surface in production: `health` and `info` only; metrics and module internals stay private
- Durable storage: Neon PostgreSQL; Render's local filesystem is temporary
- Batch execution: inside the web process; the free plan has no separate worker
- JVM ceiling: `-Xms128m -Xmx320m -XX:+ExitOnOutOfMemoryError`
- Hikari pool: maximum 3 connections and `minimumIdle=0`
- Public import ceiling: 10 MB and 25,000 rows

## Required secrets

The following values are stored only in Render and must never enter Git, screenshots, logs, or documentation:

| Variable      | Value shape                                                                               |
| ------------- | ----------------------------------------------------------------------------------------- |
| `DB_URL`      | Direct Neon JDBC endpoint with `sslmode=require`; the hostname must not contain `-pooler` |
| `DB_USERNAME` | Neon database role                                                                        |
| `DB_PASSWORD` | Neon role password                                                                        |

`DEMO_TOKEN_HASH_SECRET` uses a generated production value stored in Render. The live service pins `ALLOWED_ORIGINS` to the exact `https://reconriver.vercel.app` origin and sets `DB_POOL_SIZE=3`; the application fixes Hikari's minimum idle count at zero. All other non-secret variables are mirrored in `render.yaml`. Verify environment keys and safe shapes without copying secret values into logs or documentation.

The direct Neon endpoint is intentional. Flyway, Spring Batch metadata updates, and application transactions need normal PostgreSQL session behavior; [Neon's pooled endpoint](https://neon.com/docs/connect/connection-pooling) uses transaction-mode PgBouncer and is not the selected target. The application-side three-connection pool is already below the configured Free-instance concurrency. A production JDBC URL should have this shape:

```text
jdbc:postgresql://<direct-neon-host>/<database>?sslmode=require
```

Do not commit a concrete host, role, password, or generated token secret.

## Verification

After each manual promotion, run the repository's runtime verifier before the product journey:

```bash
STARTUP_TIMEOUT_SECONDS=360 \
  ./scripts/verify-api-runtime.sh \
  https://reconriver-api.onrender.com \
  https://reconriver.vercel.app
```

That script verifies readiness, OpenAPI, `/docs`, committed contract drift, exact trusted CORS, rejected-origin HTTP 403, health/info-only Actuator exposure, and hidden metrics/Modulith endpoints. The full browser smoke test must then create a workspace, import a stored scenario, run the Spring Batch reconciliation, resolve and reopen an exception, inspect the audit timeline, and download the report.

## Recorded public-service evidence

The following observations were recorded on 2026-08-09 against the live Render API and Neon database:

| Check                           | Observation                                                                                            |
| ------------------------------- | ------------------------------------------------------------------------------------------------------ |
| First deployment readiness      | 207 s; this is deployment startup, not an idle-service cold wake                                       |
| Maximum public import           | 25,000/25,000 synthetic internal-ledger rows imported, 0 quarantined, in 260 s                         |
| Rolling replacement             | New Spring process started in 210.494 s; the existing public service remained available                |
| Genuine idle wake               | After 20m19.9s idle, the browser reached a real workspace by +266.512 s (4m26.5s observed upper bound) |
| Mixed-exceptions API smoke      | 1,244 total results: 1,007 matched and 237 exceptions in 19.9 s                                        |
| Final mobile audit run          | 1,244 total results: 1,007 matched and 237 exceptions in 42.4 s                                        |
| Month-end close                 | 11,539 total results: 11,239 matched and 300 exceptions from 10,000 canonical payments                 |
| Controlled failure and recovery | 11,369 total results: 10,813 matched and 556 exceptions in 264.65 s from 10,000 canonical payments     |

Totals combine order-level and settlement-level results, so they can exceed the canonical-payment count.

After the rolling replacement, the same live workspace still contained 1,244 results and its resolve/reopen audit entries. This is evidence that the workflow data persisted in Neon across that replacement. No public downtime was observed because the previous service stayed available during rollout, but one event is not an availability guarantee.

For the genuine idle-wake check, the UI exposed its `role="status"` wake message and disabled both start actions by +5.3 seconds. Render began the container at +5.057 seconds, logged Spring starting at +25.802 seconds, started Tomcat at +226.099 seconds, and reported the application started in 211.098 seconds at +226.501 seconds from the click. The DispatcherServlet initialized at +232.499 seconds. The UI still showed the wake state at +243.085 seconds and was on `/scenarios` with a real workspace by +266.512 seconds. The 266.5-second figure is therefore an observed end-to-end upper bound, not an exact response time.

Do not infer requests per second, steady-state capacity, availability, or future cold-start time from these recorded service checks.

## Free-tier constraints

[Render documents](https://render.com/docs/free) that Free services scale down after 15 minutes without inbound traffic, can take about one minute to wake, use an ephemeral filesystem, and share 750 free instance hours per workspace each month. The [Free instance type](https://render.com/docs/compute-plans) has 512 MB RAM and 0.1 CPU. ReconRiver does not send keep-alive traffic, and the public API does not bundle the 100,000-row local performance scenario. If the free allowance is exhausted, availability can stop until the allowance resets; the deployment must never be upgraded automatically.
