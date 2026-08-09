# Security policy

## Data handling boundary

The hosted ReconRiver workspace accepts synthetic data only. Do not upload real payment, customer, card, bank, or personally identifiable information.

## Reporting a vulnerability

Use the repository's private GitHub Security Advisory flow. Do not open a public issue with exploit details, tokens, credentials, or uploaded data. Include the affected version, reproduction steps, impact, and a minimal synthetic proof of concept.

## Hosted-service controls

- Anonymous bearer tokens are scoped to one expiring workspace and stored only as keyed hashes.
- Uploads are restricted by origin, type, size, row count, headers, row width, and rate.
- Reports escape spreadsheet formula prefixes.
- Application queries scope business access by workspace. Database constraints require a valid workspace and protect canonical file, active-run, stable-work and result identities.
- Expired workspaces are deleted automatically.
- Secrets are supplied by environment variables and are never expected in source control.

Any change to the accepted data classification or access model requires a separate security and compliance review covering identity, authorization, retention, key management, malware scanning, and operational controls.
