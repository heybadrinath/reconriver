# Free-tier deployment notes

Provider limits below were checked on 2026-08-09 against the linked official documentation. Recheck them before each deployment because free-plan terms can change independently of this repository.

## Vercel Hobby

The Next.js frontend is intended for [Vercel Hobby](https://vercel.com/docs/plans/hobby) and calls the Java API directly to avoid unnecessary function usage. Hobby permits personal, non-commercial use; if the project's purpose becomes commercial, re-evaluate the plan before continuing. Production configuration permits only the intended `https://reconriver.vercel.app` browser origin, but that URL must not be presented as live until a deployment check succeeds.

## Render Free Web Service

Render is the sole intended backend deployment target. The checked-in `render.yaml` configures the [Free Web Service](https://render.com/docs/free) plan, the repository-root Docker build, and the readiness endpoint. Render currently documents 750 free instance hours per workspace each month, scale-down after 15 idle minutes, and a wake-up that can take about one minute; its [Free instance specification](https://render.com/docs/compute-plans) is 512 MB RAM and 0.1 CPU. Do not add a payment method or upgrade the instance; availability can stop when the included allowance is exhausted.

The intended Free filesystem is ephemeral and there is no separate worker, so the deployment configuration keeps durable state in Neon, stages uploads into PostgreSQL, runs batch jobs inside the web process, uses a three-connection Hikari pool with zero minimum idle connections, and sets a 320 MB maximum Java heap. Final verification must cover a real cold start and the 25,000-row import path on Render's constrained CPU and memory before these are described as observed runtime behavior.

## Neon Free

[Neon Free](https://neon.com/pricing) currently needs no card and has no time limit. Each project includes 100 compute-unit hours per month, 0.5 GB storage, and 5 GB public egress; [scale to zero](https://neon.com/docs/introduction/scale-to-zero) occurs after five idle minutes. Production configuration requires a direct Neon PostgreSQL endpoint over SSL (`sslmode=require`), not the [`-pooler` transaction endpoint](https://neon.com/docs/connect/connection-pooling), so Flyway and Spring Batch retain normal session behavior. The API limits Hikari to three connections with no minimum idle connections, uses short demo retention, and must tolerate database wake-up during startup. Verify the actual endpoint shape and startup behavior after Neon is connected.

## Hugging Face public dataset

[Hugging Face dataset repositories](https://huggingface.co/docs/hub/datasets-overview) support public hosting. The intended public repository is [heybadrinath/reconriver-synthetic-reconciliation](https://huggingface.co/datasets/heybadrinath/reconriver-synthetic-reconciliation). The checked-in dataset card declares CC BY 4.0, generation seeds, provenance, assumptions, and synthetic-data limitations. Publish and verify the card, CSV, JSON, and optional ZIP files before describing the remote dataset as available.
