# Security policy

## Demonstration boundary

ReconRiver is a portfolio demonstration built for synthetic data. Do not upload real payment, customer, card, bank, or personally identifiable information. It is not production financial infrastructure and has not been assessed for regulated workloads.

## Reporting a vulnerability

Use the repository's private GitHub Security Advisory flow. Do not open a public issue with exploit details, tokens, credentials, or uploaded data. Include the affected version, reproduction steps, impact, and a minimal synthetic proof of concept.

## Public demo controls

- Anonymous bearer tokens are scoped to one expiring workspace and stored only as keyed hashes.
- Uploads are restricted by origin, type, size, row count, headers, row width, and rate.
- Reports escape spreadsheet formula prefixes.
- Application queries scope business access by workspace. Database constraints require a valid workspace and protect canonical file, active-run, stable-work and result identities.
- Expired workspaces are deleted automatically.
- Secrets are supplied by environment variables and are never expected in source control.

These controls reduce demo risk; they are not a substitute for production identity, authorization, audit retention, data classification, key management, malware scanning, or compliance review.
