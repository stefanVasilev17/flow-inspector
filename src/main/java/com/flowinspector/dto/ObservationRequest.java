package com.flowinspector.dto;

import com.flowinspector.domain.FlowStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/** Incoming state change from an instrumented service or a manual API caller. */
public record ObservationRequest(
        @NotBlank @Size(max = 128) String traceId,
        @NotBlank @Size(max = 128) String eventId,
        @Size(max = 128) String parentEventId,
        @NotBlank @Size(max = 100) String service,
        @NotBlank @Size(max = 160) String operation,
        @NotNull FlowStatus status,
        Instant occurredAt,
        @Size(max = 1000) String message,
        @Size(max = 200) String exceptionType,
        @Size(max = 240) String sourceClass,
        Integer sourceLine
) { }
