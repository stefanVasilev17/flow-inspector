package com.flowinspector.domain;

import java.time.Instant;

/** One append-only state transition; eventId groups transitions for an operation. */
public record Observation(
        long sequence,
        String traceId,
        String eventId,
        String parentEventId,
        String service,
        String operation,
        FlowStatus status,
        Instant occurredAt,
        String message,
        String exceptionType,
        String sourceClass,
        Integer sourceLine
) { }
