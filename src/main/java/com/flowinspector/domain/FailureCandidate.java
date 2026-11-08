package com.flowinspector.domain;

import java.time.Instant;

/** First observed failure, not a proven causal root cause. */
public record FailureCandidate(
        String eventId,
        String service,
        String operation,
        Instant occurredAt,
        String message,
        String exceptionType,
        String sourceClass,
        Integer sourceLine
) { }
