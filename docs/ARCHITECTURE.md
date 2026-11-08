# Architecture — Layered Java backend

## Dependencies

```text
Controller:  HTTP input and response only
    ↓
Service:     Flow recording, OTLP mapping, graph reconstruction, SSE delivery
    ↓
Repository:  SQL/JDBC or bounded memory, append-only observations
```

`ObservationRepository` is the stable storage boundary. `@Profile("!postgres")` uses `InMemoryObservationRepository`; `@Profile("postgres")` uses `PostgresObservationRepository`. No repository depends on HTTP or SSE. `OtlpPayloadDecoder` is a protocol helper in `util/` and performs Protobuf, Protobuf-JSON and gzip parsing. `OtlpTraceService` validates and converts OTLP spans and delegates persistence to `ObservationService`.

## Collection and read paths

```text
Java agent/SDK (OTLP/HTTP) --> OtlpIngestionController
                                       |
                                OtlpTraceService --- OtlpPayloadDecoder
                                       |
Manual domain state ---------> FlowController
                                       |
                                ObservationService --------> LiveEventService --> SSE
                                       |
                                ObservationRepository
                                  |               |
                               InMemory       PostgreSQL
                                  |               |
                                  +-------+-------+
                                          |
                              TraceService + FlowAnalyzer
                                          |
                                      REST + UI
```

OTLP spans are represented by two append-only `Observation` records with the same span ID (`eventId`), one at span start and one at span end. Parent spans are `parentEventId`. The OTel resource attribute `service.name` becomes `service`, and span.name becomes `operation`. `STATUS_CODE_ERROR` becomes `FAILED`. Diagnostic metadata is included only if present; no Java source line is inferred.

The UI groups events by ID, reconstructs parent edges, and highlights the earliest **observed** FAILED event. This is a useful debugging lead but **not proof of root cause** because observed timestamps can be unsynchronized, and some telemetry can be absent.

## PostgreSQL storage

Schema: `src/main/resources/db/migration/V1__create_observations.sql`. One row per state transition, ordered by server-generated sequence when timestamps tie. Timestamp type is PostgreSQL `TIMESTAMPTZ`. Per-trace transactional advisory locks prevent simultaneous writers from bypassing the 500-observation UI limit. The list endpoint returns the most recently updated 200 traces. Long-term retention and efficient bulk-read pagination are not yet implemented. Queries and writes are handled through `JdbcTemplate`; no JPA entities are needed for this append-only model.

## OTLP compatibility

`POST /v1/traces` accepts binary `ExportTraceServiceRequest` with `application/x-protobuf` (normal Java agent default when configured for OTLP/HTTP). It also accepts the protocol's JSON mapping with `application/json`, optionally gzip encoded. Request validation is done before persistence, but the batch is **not fully atomic** when storage fails partway. Success is the empty `ExportTraceServiceResponse` (`200` and empty protobuf or `{}` JSON). Invalid binary and missing span/trace IDs produce `400`; body/span limits produce `413`. For production ingestion, a Collector and queue with reliable at-least-once handling, deduplication and backpressure are needed.

## Security and next engineering tasks

The current server defaults to loopback and Docker Compose maps only localhost ports. The application is **not authenticated** and is unsuitable for direct production traffic. Before deployment implement key authentication or mTLS, per-tenant separation, redact/allowlist attributes, ingestion rate limits, storage retention, retry-safe deduplication, asynchronous bulk ingest, and encrypted connections/storage. Separate technical traces from explicit business-state semantics; add a Java Spring starter/SDK later for structured domain events, Kafka correlation and source-code links.
