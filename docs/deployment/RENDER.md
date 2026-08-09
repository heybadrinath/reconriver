# Render deployment

ReconRiver is configured to deploy its Spring Boot API as the `reconriver-api` [Render Free Web Service](https://render.com/docs/free) in Frankfurt. The repository's `render.yaml` is the source of truth for the intended Docker build, health check, free instance type, and non-secret runtime settings. Do not describe the service as live until the assigned Render URL passes the verification below.

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

Render should prompt for the following values during the first Blueprint sync. They must remain in Render, never in Git:

| Variable      | Value shape                                                                               |
| ------------- | ----------------------------------------------------------------------------------------- |
| `DB_URL`      | Direct Neon JDBC endpoint with `sslmode=require`; the hostname must not contain `-pooler` |
| `DB_USERNAME` | Neon database role                                                                        |
| `DB_PASSWORD` | Neon role password                                                                        |

`DEMO_TOKEN_HASH_SECRET` is configured for Render-generated storage. The Blueprint pins `ALLOWED_ORIGINS` to the intended exact `https://reconriver.vercel.app` origin and sets `DB_POOL_SIZE=3`; the application fixes Hikari's minimum idle count at zero. All other non-secret variables are declared in `render.yaml`. Verify the resolved environment values in Render without copying secret values into logs or documentation.

The direct Neon endpoint is intentional. Flyway, Spring Batch metadata updates, and application transactions need normal PostgreSQL session behavior; [Neon's pooled endpoint](https://neon.com/docs/connect/connection-pooling) uses transaction-mode PgBouncer and is not the selected target. The application-side three-connection pool is already below the configured Free-instance concurrency. A production JDBC URL should have this shape:

```text
jdbc:postgresql://<direct-neon-host>/<database>?sslmode=require
```

Do not commit a concrete host, role, password, or generated token secret.

## Verification

After deployment, verify both a warm and cold request:

```bash
API_BASE_URL="https://<assigned-render-host>"
curl --fail --show-error "$API_BASE_URL/actuator/health/readiness"
curl --fail --show-error "$API_BASE_URL/v3/api-docs" >/dev/null
curl --fail --show-error -H 'Origin: https://reconriver.vercel.app' \
  -D - -o /dev/null "$API_BASE_URL/actuator/health/readiness" \
  | grep -Fi 'access-control-allow-origin: https://reconriver.vercel.app'
```

Repeat the CORS request with an unrelated origin and verify that no `Access-Control-Allow-Origin` header is returned. The full browser smoke test must then create a demo session, import a stored scenario, run the real Spring Batch reconciliation, resolve and reopen an exception, inspect the audit timeline, and download the report.

## Free-tier constraints

[Render documents](https://render.com/docs/free) that Free services scale down after 15 minutes without inbound traffic, can take about one minute to wake, use an ephemeral filesystem, and share 750 free instance hours per workspace each month. The [Free instance type](https://render.com/docs/compute-plans) has 512 MB RAM and 0.1 CPU. ReconRiver does not send keep-alive traffic, and the public API does not bundle the 100,000-row local performance scenario. If the free allowance is exhausted, availability can stop until the allowance resets; the deployment must never be upgraded automatically.
