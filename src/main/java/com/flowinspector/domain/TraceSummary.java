package com.flowinspector.domain;

import java.time.Instant;
import java.util.List;

public record TraceSummary(
        String traceId,
        FlowStatus status,
        Instant startedAt,
        Instant updatedAt,
        int observationCount,
        List<String> services,
        String firstFailureService
) { }
