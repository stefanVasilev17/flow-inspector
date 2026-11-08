# Production Flow Inspector

**See where a production execution flow breaks — from service calls to business-event state changes.**

Java 21 · Spring Boot 4.1.2 · Maven · REST API · Server-Sent Events · Dark-mode trace explorer

> **Status: local MVP / architectural starter, not production-ready.** This release accepts manual events or events sent by a custom client. It does **not** yet ingest OpenTelemetry traces automatically or infer actual Java lines from deployed services.

## What is in this repository?

- `POST /api/v1/observations` — append state transitions of one operation.
- `GET /api/v1/traces` — list discovered execution traces.
- `GET /api/v1/traces/{traceId}` — reconstructed graph, state history and first observed failure.
- `GET /api/v1/stream` — SSE notifications for UI refresh.
- `POST /api/v1/demo` — synthetic payment → risk → fingerprint/provider example.
- `/` — interactive dashboard with graph, timeline, first-failure banner and per-node inspection.
- JUnit tests and CI workflow.

## Project structure — Controller / Service / Repository

```text
src/main/java/com/flowinspector/
  controller/   # REST endpoints and request validation only
  service/      # Business use cases, graph analysis, SSE, demo flow
  repository/   # ObservationRepository interface + in-memory implementation
  dto/          # ObservationRequest input record
  domain/       # Observation, trace graph, state and failure records
  exception/    # Typed errors + global REST exception handler
```

**Dependency direction:** `Controller → Service → Repository`. The controller never calls the repository directly. A storage change (for example PostgreSQL) does not require controller changes. Business states remain append-only, and the existing HTTP contracts and browser dashboard are unchanged. See [architecture and package boundaries](docs/ARCHITECTURE.md).

## Run locally

Requirements: **JDK 21+** and **Maven 3.9+**. From the repository root:

```bash
mvn spring-boot:run
```

Open **http://127.0.0.1:8080** and click **Generate demo trace**. You will see a simulated failure:

```text
payment-service: checkout (RECEIVED → RUNNING → FAILED)
  └─ risk-rule-engine: evaluateRisk (RUNNING → FAILED)
       ├─ fingerprint-service: loadDevice (RUNNING → SUCCEEDED)
       └─ fraud-provider-adapter: getRiskScore (RUNNING → FAILED)
            SocketTimeoutException @ FraudClient:142 [synthetic demo metadata]
```

### Send your own observation

```bash
curl -X POST http://127.0.0.1:8080/api/v1/observations \
  -H 'Content-Type: application/json' \
  -d '{
    "traceId": "my-trace-1001",
    "eventId": "risk-check",
    "parentEventId": null,
    "service": "risk-rule-engine",
    "operation": "evaluateRisk",
    "status": "RUNNING",
    "message": "Started risk evaluation",
    "sourceClass": "RiskService",
    "sourceLine": 102
  }'
```

Send another observation with the **same `traceId` and `eventId`**, but `status: "FAILED"`, `exceptionType: "SocketTimeoutException"` and a later `occurredAt`. The UI updates automatically via SSE, and the node's history shows the transition.

**Observation fields:** `traceId`, `eventId`, `service`, `operation`, and `status` are required. `status` is one of `RECEIVED`, `RUNNING`, `SUCCEEDED`, `FAILED`, `SKIPPED`. `parentEventId`, `occurredAt`, `message`, `exceptionType`, `sourceClass`, and `sourceLine` are optional.

### Tests

```bash
mvn verify
```

### Docker demo (local-only port binding)

```bash
docker build -t production-flow-inspector .
docker run --rm -p 127.0.0.1:8080:8080 production-flow-inspector
```

## How the algorithm works

A `traceId` is an execution; an `eventId` is an operation. Every POST stores a new immutable observation instead of replacing an old one. The backend groups observations by `eventId`, takes the latest state, and connects nodes through `parentEventId`. It sorts events by producer timestamp (then ingest sequence), and highlights the first event reporting `FAILED` as the **first observed failure**.

The first observed failure is **not necessarily the causal root cause**: missing spans, retries, unsynchronized clocks and upstream failures can change the interpretation. That distinction is part of the product design.

## Why OpenTelemetry later?

OpenTelemetry Java agent is the recommended starting point for automatically instrumenting Spring Boot frameworks and HTTP/DB calls; domain events still need explicit signals. This MVP deliberately starts with the event-state model and investigation UX, then adds a real OpenTelemetry Collector / OTLP ingestion pipeline. See [architecture](docs/ARCHITECTURE.md) and [product roadmap](docs/PRODUCT.md).

Official references: [OpenTelemetry Java instrumentation](https://opentelemetry.io/docs/languages/java/instrumentation/), [OpenTelemetry Spring Boot starter](https://opentelemetry.io/docs/zero-code/java/spring-boot-starter/).

## Constraints and security

The backend uses an in-memory store (maximum **200 traces** and **500 observations per trace**), so data disappears on restart. No authentication, tenant boundaries, rate-limits or automatic sensitive-data redaction exist yet. The application binds to `127.0.0.1` by default; the Docker image binds internally to `0.0.0.0`, so publish it only on the host loopback as demonstrated. **Never connect this unprotected starter directly to a production environment or send sensitive business data to it.**

## Repository ownership and licensing

No open-source license is selected. Decide whether to keep this as a private commercial project or publish with a license before distributing it. Project/product name is a working title; verify branding before commercial use.
