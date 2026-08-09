# ADR 0003: Use expiring anonymous workspaces

- Status: accepted
- Date: 2026-08-09

## Decision

Allow anonymous demonstrations through a high-entropy bearer token scoped to one workspace for 24 hours. Persist only a keyed hash, isolate every query by workspace, cap uploads at 10 MB and 25,000 rows, rate-limit public requests, and automatically delete expired workspaces.

## Rationale

Registration would weaken the portfolio demo. Unbounded anonymous uploads would create privacy, abuse, and cost risks. The selected model collects no identity data and makes the synthetic-data boundary explicit.

## Consequences

Session storage loss or expiry starts a fresh workspace. This is not an authentication model for real financial data. Production use would require organization identity, roles, stronger distributed rate limiting, retention policy, malware scanning, and compliance controls.
