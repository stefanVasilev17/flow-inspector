# Product concept — Production Flow Inspector

## User problem

An engineer gets an alert saying checkout failed. Logs are scattered across payment-service, risk-rule-engine, Redis, Kafka consumers and third-party adapters. The last exception is often a consequence of a fault somewhere upstream. Engineers must manually reconstruct what happened to one request or business event.

## Product promise

**Show the observed execution path, how every operation changed state, where the first error was observed, and what evidence to inspect next.**

## Target users

Java backend engineers, SREs, platform teams and engineering leads operating service-based systems, especially systems with multi-step, asynchronous business events.

## Product differentiators beyond a trace viewer

1. An **event state history** (RECEIVED → RUNNING → FAILED) for each business operation, not just span duration.
2. A **timeline + dependency graph** on the same screen.
3. A **first observed failure** that points to an exception and optional Java class/line hint; it never incorrectly claims to have proven causal root cause.
4. Eventually: Kafka async-correlation, retry/attempt visualization, business state machines, deployed commit + code navigation, and AI explanations with evidence links.

## MVP (implemented)

- Append-only observation API.
- Operation grouping by traceId/eventId.
- parentEventId edges.
- Latest state + full state history.
- First observed FAILED transition.
- Live UI refresh via Server-Sent Events.
- Synthetic checkout/risk/provider demonstration.

## Not implemented yet

- Native OTLP/Jaeger/Tempo span ingestion or OpenTelemetry collector receiver.
- Automatic bytecode tracing or instrumentation of external applications.
- Persistent storage, multiple tenant support, production access control.
- Reliable root-cause analysis, code-line mapping without an explicit source hint, Kafka context propagation, or AI assistant.

## Suggested next implementation milestones

**Milestone 1 — Real-world data:** integrate OpenTelemetry Collector via OTLP and ingest standard spans while preserving optional domain events. Use the Java agent for common frameworks; add explicit domain events only where they add value.

**Milestone 2 — Correlation:** traceparent across HTTP and Kafka, external business correlation key, retries, dead-letter queues, and out-of-order events; handle partial/missing spans and sampling.

**Milestone 3 — Operability:** PostgreSQL/ClickHouse or an observability-friendly backend, retention, query filters, rate limiting, redaction, RBAC, and tenants.

**Milestone 4 — Investigation:** map stack frames to source commit, build failure propagation graph, rank *hypotheses* with evidence and confidence, integrate GitHub/Jira, evaluate AI explanations.

## Success metric

Median time from production incident selection to the first useful investigation step. Compare with manual search in existing logs/traces; do not use "exact root cause" as a success metric without a labeled evaluation dataset.


## Phase 2 implementation (current)

- PostgreSQL 17 append-only observation repository with Flyway migrations, gated behind `postgres` Spring profile.
- Native OTLP/HTTP trace receiver for protobuf/JSON/gzip, mapping span start/end and status to observations.
- Docker Compose stack and CI integration testing against PostgreSQL.
- Missing for production: OTLP idempotency, authentication, retention, quotas, reliable async ingestion and sensitive data redaction.
