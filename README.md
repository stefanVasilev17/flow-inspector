# Production Flow Inspector

**See how a request moves through Java microservices, which states it reached, and the first observed failure.**

Java 21 · Spring Boot 4.1.2 · PostgreSQL 17 · Flyway · OpenTelemetry OTLP/HTTP · SSE dashboard

> **Status: development MVP, not production-ready.** OTLP ingestion is real, but the receiver currently has no authentication, tenant isolation, automatic attribute sanitization, ingestion backpressure or data retention. **Do not expose it publicly or send sensitive production traces.**

## What it does

- `POST /v1/traces` — standard OTLP/HTTP traces, Protobuf or JSON, including gzip compressed requests.
- `POST /api/v1/observations` — append manual **business-state** observations (`RECEIVED`, `RUNNING`, `SUCCEEDED`, `FAILED`, `SKIPPED`).
- `GET /api/v1/traces` — recent traces; `GET /api/v1/traces/{traceId}` — reconstructed execution graph.
- `GET /api/v1/stream` — SSE live update notifications; `POST /api/v1/demo` — generated example.
- `/` — execution graph, timeline, per-event states, and first observed failure.
- PostgreSQL via `postgres` Spring profile; in-memory default profile for fast standalone demos.

**Important distinction:** OpenTelemetry auto-instrumentation captures technical operations (HTTP calls, JDBC, Kafka, etc.); it does not automatically know domain states like `RISK_ACCEPTED` or `PAYMENT_REJECTED`. For domain states use the manual observations API (future: a small Java SDK). Error spans and exception status help find the **first observed error**, not prove causal root cause. Source class/line appear only if the span explicitly supplies compatible code attributes.

## Run — standalone in-memory demo (no database required)

Requires JDK 21+ and Maven 3.9+:

```bash
mvn spring-boot:run
```

Open **http://127.0.0.1:8080** and click **Generate demo trace**. Default observations are volatile, bounded to 200 traces and 500 observations/trace.

## Run — persistent PostgreSQL with Docker Compose

Requires Docker with Compose:

```bash
docker compose up --build
```

Dashboard: **http://127.0.0.1:8080**. The Compose application uses the `postgres` Spring profile, runs Flyway migration `V1__create_observations.sql`, and stores state transitions in the named Docker volume `flow_pgdata`. Restarting the application retains its observations.

To use a locally installed Maven/Java app with only PostgreSQL in Docker:

```bash
docker compose up -d postgres
SPRING_PROFILES_ACTIVE=postgres mvn spring-boot:run
```

You can set `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD` environment variables to override local defaults. The bundled password is **only for local development**. Docker/Compose exposes both ports on host loopback (`127.0.0.1`) only; the app listens on the Docker network internally. Do not reuse these defaults for a remote database. PostgreSQL currently has **no automatic retention**; the UI lists only the 200 most recently written traces.

## Connect a Java service through the OpenTelemetry Java Agent

Use a current compatible [OpenTelemetry Java instrumentation agent](https://opentelemetry.io/docs/zero-code/java/agent/) with a separate Java/Spring application. Example JVM environment:

```bash
export OTEL_SERVICE_NAME=payment-service
export OTEL_TRACES_EXPORTER=otlp
export OTEL_METRICS_EXPORTER=none
export OTEL_LOGS_EXPORTER=none
export OTEL_EXPORTER_OTLP_PROTOCOL=http/protobuf
export OTEL_EXPORTER_OTLP_TRACES_ENDPOINT=http://127.0.0.1:8080/v1/traces
java -javaagent:/path/to/opentelemetry-javaagent.jar -jar payment-service.jar
```

In Docker networking, replace `127.0.0.1:8080` with the Flow Inspector container hostname and port. The receiver accepts the standard **OTLP trace protocol**, not Jaeger or Zipkin JSON. It accepts `application/x-protobuf`, `application/json`, optional `Content-Encoding: gzip`, a maximum **4 MiB** (compressed and uncompressed), and **200 spans per request**. Each completed span is stored as `RUNNING` at its start timestamp and either `SUCCEEDED` or `FAILED` at its end timestamp. A recorded exception alone does **not** make an otherwise successful span fail; the span status must be `STATUS_CODE_ERROR`.

A `traceId` is 16 bytes (32 hexadecimal characters) and a `spanId` is 8 bytes (16 hex chars). Parent span ID links nodes across service boundaries when trace context propagation is configured. **Only trace data** is implemented, not OTLP metrics or logs. OTLP retry deduplication and atomic persistence of whole export batches are not implemented in this stage; repeated exports can add duplicate transitions. Keep collector/agent retries and per-trace limits in mind.

### Manual business-state event

```bash
curl -X POST http://127.0.0.1:8080/api/v1/observations \
  -H 'Content-Type: application/json' \
  -d '{
    "traceId": "custom-trace-1001",
    "eventId": "risk-evaluation",
    "parentEventId": null,
    "service": "risk-rule-engine",
    "operation": "evaluateRisk",
    "status": "RUNNING",
    "message": "Started risk evaluation"
  }'
```

Send a later `FAILED` observation with the **same traceId + eventId** and a relevant `exceptionType` to see a state transition. Correlating with automatic OTLP spans requires using their hexadecimal trace and span IDs.

## Architecture

```text
Java services + OTel Java agent ------> OTLP/HTTP /v1/traces ------> OtlpTraceService
Java business events -----------------> REST /api/v1/observations -> ObservationService
                                                                      |
                                                             ObservationRepository
                                                               /          \
                                                    InMemory (default)   PostgreSQL (profile)
                                                                      |
                                               TraceService -> FlowAnalyzer -> UI + SSE
```

```text
src/main/java/com/flowinspector/
  controller/  FlowController, DemoController, LiveEventsController, OtlpIngestionController
  service/     ObservationService, OtlpTraceService, TraceService, FlowAnalyzer, LiveEventService
  repository/  ObservationRepository, InMemoryObservationRepository, PostgresObservationRepository
  domain/      Observation, FlowNode, FlowEdge, FlowSnapshot, FlowStatus, FailureCandidate
  dto/         ObservationRequest
  util/        OtlpPayloadDecoder (binary/JSON/gzip protocol parsing)
  exception/   Typed errors + GlobalExceptionHandler
```

**Rule:** controllers bind HTTP requests, services implement use cases and translate spans into observations, repositories deal with storage. No controller talks directly to the database. PostgreSQL inserts are append-only; Flyway manages schema creation.

Read [Architecture](docs/ARCHITECTURE.md) and [Product plan](docs/PRODUCT.md).

## Tests and CI

```bash
mvn verify
```

Default tests use in-memory mode. PostgreSQL integration tests run when `RUN_POSTGRES_IT=true` is set and a PostgreSQL 17 database is reachable at `DB_URL`. GitHub Actions provisions PostgreSQL automatically and runs both test groups on every push and PR.

## Key constraints before connecting real production systems

1. Add authentication, tenant isolation and fine-grained permissions to UI, query and ingestion endpoints.
2. Introduce strict allowlist/redaction of exception messages, resource attributes and identifiers; even an error message can include secrets or personal data.
3. Implement deterministic OTLP retry deduplication, batching, backpressure, retention, quotas and pagination.
4. Handle multi-instance SSE event delivery; current SSE clients are local to one JVM.
5. Improve ordering and causal diagnosis under unsynchronized clocks, sampling, retries, missing parents and async Kafka flows.

The source has no open-source license chosen yet. Review licensing and branding before redistribution.
