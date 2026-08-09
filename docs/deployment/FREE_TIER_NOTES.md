# Free-tier deployment notes

Provider limits below were checked on 2026-08-09 against the linked official documentation. Recheck them before each deployment because free-plan terms can change independently of this repository.

## Vercel Hobby

The [live Next.js frontend](https://reconriver.vercel.app) uses [Vercel Hobby](https://vercel.com/docs/plans/hobby) and calls the Java API directly to avoid unnecessary function usage. The current plan is governed by non-commercial-use terms and must be re-evaluated before any commercial use. The deployed backend permits exactly `https://reconriver.vercel.app` as the browser origin.

## Render Free Web Service

The [live API](https://reconriver-api.onrender.com/docs) uses a [Render Free Web Service](https://render.com/docs/free) in Frankfurt. The checked-in `render.yaml` records the repository-root Docker build, health endpoint, free plan, and non-secret settings. The live service was created through Render's official API with equivalent values; it is not Blueprint-linked. Automatic deployment is disabled on the live service so a tested commit can be promoted deliberately, while the checked-in file retains a `checksPass` trigger for future Blueprint use.

Render currently documents 750 free instance hours per workspace each month, scale-down after 15 idle minutes, and a wake-up that can take about one minute; its [Free instance specification](https://render.com/docs/compute-plans) is 512 MB RAM and 0.1 CPU. Do not add a payment method or upgrade the instance; availability can stop when the included allowance is exhausted.

The Free filesystem is ephemeral and there is no separate worker, so the deployment keeps durable state in Neon, stages uploads into PostgreSQL, runs batch jobs inside the web process, uses a three-connection Hikari pool with zero minimum idle connections, and sets a 320 MB maximum Java heap. The initial deployment reached readiness in 207 seconds. A separate rolling replacement started Spring in 210.494 seconds, retained the same Neon-backed workspace data, and produced no observed public downtime while the old service remained available. Neither startup is an idle-service cold-start measurement.

The maximum public import path was exercised with a 25,000-row synthetic internal-ledger CSV: 25,000 rows imported, zero were quarantined, and the import completed in 260 seconds.

A separate cold-start check began after 20m19.9s without public API activity. The UI showed its accessible wake message and disabled start actions by +5.3 seconds. Render reported the Spring application started in 211.098 seconds (+226.501 seconds from the first user action), while the browser reached `/scenarios` with a real workspace by +266.512 seconds. Record this as an observed end-to-end upper bound of 266.5 seconds (4m26.5s), not an exact response time or guarantee for future wakes.

## Neon Free

[Neon Free](https://neon.com/pricing) currently needs no card and has no time limit. Each project includes 100 compute-unit hours per month, 0.5 GB storage, and 5 GB public egress; [scale to zero](https://neon.com/docs/introduction/scale-to-zero) occurs after five idle minutes. The deployed API uses a verified direct Neon PostgreSQL endpoint over SSL (`sslmode=require`), not the [`-pooler` transaction endpoint](https://neon.com/docs/connect/connection-pooling), so Flyway and Spring Batch retain normal session behavior. The API limits Hikari to three connections with no minimum idle connections, and 24-hour workspaces keep data retention short. Credentials remain only in Render.

## Hugging Face public dataset

[Hugging Face dataset repositories](https://huggingface.co/docs/hub/datasets-overview) support public hosting. [heybadrinath/reconriver-synthetic-reconciliation](https://huggingface.co/datasets/heybadrinath/reconriver-synthetic-reconciliation) is public and synthetic-only. Its dataset card declares CC BY 4.0, generation seeds, provenance, assumptions, and limitations. All 16 scenario/file configurations materialize in Dataset Viewer, and a downloaded publication snapshot matched all 32 committed SHA-256 entries.
