# Architecture and package structure

## Spring layers

```text
com.flowinspector
├── FlowInspectorApplication.java
├── controller/
│   ├── FlowController.java             # REST observations + trace queries
│   ├── DemoController.java             # REST demo endpoint
│   └── LiveEventsController.java       # REST SSE endpoint
├── service/
│   ├── ObservationService.java         # Persist and publish observations
│   ├── TraceService.java               # Trace query use cases
│   ├── FlowAnalyzer.java               # Deterministic flow reconstruction
│   ├── DemoFlowService.java            # Scheduled synthetic transitions
│   └── LiveEventService.java           # SSE subscriptions and heartbeats
├── repository/
│   ├── ObservationRepository.java      # Storage contract
│   └── InMemoryObservationRepository.java  # Local-only implementation
├── dto/
│   └── ObservationRequest.java         # REST input and validation
├── domain/
│   ├── Observation.java                # Stored transition
│   ├── FlowStatus.java                  # State enum
│   ├── FlowNode.java / FlowEdge.java   # Graph model
│   ├── FlowSnapshot.java               # Reconstructed trace
│   ├── TraceSummary.java               # List item
│   └── FailureCandidate.java           # First observed failure
└── exception/
    ├── TraceNotFoundException.java
    ├── ObservationLimitExceededException.java
    └── GlobalExceptionHandler.java     # Domain errors -> HTTP codes
```

### Call directions

```text
Client --> Controller --> Service --> Repository
                          |             |
                          |             +-- InMemoryObservationRepository
                          +-- FlowAnalyzer
                          +-- LiveEventService --> SSE clients

Ingestion: FlowController -> ObservationService -> ObservationRepository.save()
                                             \-- after save -> LiveEventService.publish()
Query:     FlowController -> TraceService -> ObservationRepository.find*()
                                      \---> FlowAnalyzer
Demo:      DemoController -> DemoFlowService -> ObservationService
SSE:       LiveEventsController -> LiveEventService
```

**Boundary rules:** controllers only bind and validate requests and return service results; services implement use cases and orchestration; repositories store and query data without HTTP or SSE responsibilities. Exceptions belong to `exception/`; `@RestControllerAdvice` turns them into error responses. `dto/` holds HTTP input, while `domain/` contains records for the trace model. No util/helper classes are needed yet: add them only for genuinely shared, reusable logic.

## MVP event contract

`traceId` correlates one request end-to-end; `eventId` identifies a logical operation (potentially many transitions); `parentEventId` links child operation to parent in the same trace; `occurredAt` is the producer timestamp, with server-side sequence for ties. The first FAILED observation is the *first observed failure*, not a proven root cause.

## Present data path

```text
External Java services / manual POST
              |
      Controller (validation)
              |
    ObservationService
          /       \
Repository.save   LiveEventService.publish -> /stream (browser)
          |
    TraceService -> FlowAnalyzer -> /traces* (browser)
```

The repository is intentionally memory-only, bounded (200 traces, 500 observations/trace), and cleared on restart. It is not suitable for production retention. Replace `InMemoryObservationRepository` with a PostgreSQL implementation of `ObservationRepository` when persistent storage is required. Keep the interface stable and add integration tests for a new implementation.

## Production architecture target (not implemented)

```text
Java OpenTelemetry Agent / SDK ----> OTel Collector ---> Trace store
Domain Event SDK ------------------> Event ingestion ---> State store
                                 Correlation + attribution
                                          |
                                  Query / streaming API
                                          |
                              Web execution graph + code links
```

OpenTelemetry spans describe infrastructure calls; explicit domain events describe state transitions. A real collector, authenticated ingestion, per-tenant isolation, safe redaction, quotas and retention must be implemented before processing production data. Avoid raw payloads, passwords, payment data or personal identifiers. Producer clock skew, sampling, retries and missing spans make absolute ordering uncertain.
