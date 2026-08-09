# Local performance checks

Performance evidence is collected locally and never inferred from generated counts or free-provider specifications.

## Generator measurement

```bash
./load-tests/measure-generator.sh
```

The script builds with Java 21, generates 10,000 and 100,000 canonical-payment scenarios with seed 42, and captures macOS `/usr/bin/time -l` output under `load-tests/results/`. Generated 100,000-record files and raw measurements are intentionally ignored by Git.

Record before sharing a result:

- machine model, CPU, memory, and macOS version;
- Java version and JVM options;
- generator version, seed, start date, and record count;
- wall time, maximum resident memory, and output checksums;
- whether IDs, row counts, money invariants, and ground truth passed.

## Reconciliation measurement

Start PostgreSQL and the API, load the generated scenario through the real import endpoint, start a reconciliation run, and poll its progress endpoint until a terminal state. Record the Spring Batch job and step durations, processed/skipped counts, JVM heap, PostgreSQL version, and duplicate-result query. Do not use the 100,000-record scenario against the public free service.

For a repeatable API journey, run:

```bash
./load-tests/smoke-api.sh http://localhost:8080 mixed-exceptions
./load-tests/smoke-api.sh http://localhost:8080 failure-recovery
```

The first command verifies session creation, scenario imports, reconciliation, exception resolution and reopening, audit history, and CSV export. The second also verifies the one-time five-chunk failure and bodyless restart. Set `SMOKE_TIMEOUT_SECONDS` when a cold free service needs a longer polling window. The script never prints the bearer token.

No local measurement is a production scalability claim.
