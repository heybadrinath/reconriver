# ADR 0001: Use a Spring Modulith modular monolith

- Status: accepted
- Date: 2026-08-09

## Decision

Build one Spring Boot deployment with package-by-feature application modules verified by Spring Modulith. Modules communicate through public application interfaces and durable in-process events where asynchronous separation adds value.

## Rationale

The product needs consistent monetary writes, restartable batch jobs, and audit events more than independent scaling. A modular monolith keeps those transactions explicit, fits a constrained free instance, and still makes boundaries testable. Kafka and independent services would add failure modes, cost, and operational work without improving the demonstration.

## Consequences

The API and batch worker share one process and database. A future production version could externalize selected Modulith events to a broker after the core transactional model and workload justify it.
