# ADR 0002: Use deterministic matching and decimal money

- Status: accepted
- Date: 2026-08-09

## Decision

Use `BigDecimal` throughout the money path. PostgreSQL stores monetary values as `NUMERIC(19,4)`, and the reconciliation engine normalizes comparisons to four decimal places with `HALF_EVEN`. Currency-level processor fees are first rounded to two decimal places with `HALF_UP`, then normalized for storage. Matching rules run in documented precedence order and always return a rule code plus a plain-language explanation. Fuzzy candidates remain operator-confirmed suggestions and never become automatic matches.

## Rationale

Reconciliation must be repeatable and explainable. Binary floating-point and probabilistic acceptance would make monetary differences or result provenance difficult to defend.

## Consequences

The generator, imports, database schema, engine, reports, and tests share the same amount policy. Rule changes require versioned tests and migration consideration because they can change historical interpretation.
