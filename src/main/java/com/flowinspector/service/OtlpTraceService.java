package com.flowinspector.service;

import com.flowinspector.domain.FlowStatus;
import com.flowinspector.dto.ObservationRequest;
import com.flowinspector.exception.InvalidOtlpPayloadException;
import com.flowinspector.exception.OtlpPayloadTooLargeException;
import com.flowinspector.util.OtlpPayloadDecoder;
import com.google.protobuf.ByteString;
import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceRequest;
import io.opentelemetry.proto.common.v1.AnyValue;
import io.opentelemetry.proto.common.v1.KeyValue;
import io.opentelemetry.proto.resource.v1.Resource;
import io.opentelemetry.proto.trace.v1.Span;
import io.opentelemetry.proto.trace.v1.Status;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/** Converts OTLP spans into the same append-only state timeline used by the manual API. */
@Service
public class OtlpTraceService {
    private static final int MAX_SPANS_PER_REQUEST = 200;
    private final OtlpPayloadDecoder decoder;
    private final ObservationService observationService;

    public OtlpTraceService(OtlpPayloadDecoder decoder, ObservationService observationService) {
        this.decoder = decoder;
        this.observationService = observationService;
    }

    public int ingest(byte[] body, String contentType, String contentEncoding) {
        ExportTraceServiceRequest request = decoder.decode(body, contentType, contentEncoding);
        List<ObservationRequest> transitions = new ArrayList<>();
        int spanCount = 0;
        for (var resourceSpans : request.getResourceSpansList()) {
            String service = serviceName(resourceSpans.getResource());
            for (var scopeSpans : resourceSpans.getScopeSpansList()) {
                for (Span span : scopeSpans.getSpansList()) {
                    if (++spanCount > MAX_SPANS_PER_REQUEST) {
                        throw new OtlpPayloadTooLargeException("OTLP request supports at most 200 spans");
                    }
                    transitions.addAll(convert(span, service));
                }
            }
        }
        // Build and validate the entire request before writing any observations.
        for (ObservationRequest observation : transitions) {
            observationService.record(observation);
        }
        return spanCount;
    }

    private static List<ObservationRequest> convert(Span span, String service) {
        if (span.getTraceId().size() != 16 || span.getSpanId().size() != 8
                || (!span.getParentSpanId().isEmpty() && span.getParentSpanId().size() != 8)) {
            throw new InvalidOtlpPayloadException("OTLP span needs a 16-byte traceId and 8-byte spanId");
        }
        String traceId = toHex(span.getTraceId());
        String spanId = toHex(span.getSpanId());
        String parent = span.getParentSpanId().isEmpty() ? null : toHex(span.getParentSpanId());
        String operation = bounded(span.getName().isBlank() ? "unnamed-operation" : span.getName(), 160);
        Instant startedAt = fromNanos(span.getStartTimeUnixNano(), Instant.now());
        Instant endedAt = fromNanos(span.getEndTimeUnixNano(), startedAt);
        if (endedAt.isBefore(startedAt)) {
            endedAt = startedAt;
        }

        boolean failed = span.hasStatus() && span.getStatus().getCode() == Status.StatusCode.STATUS_CODE_ERROR;
        String exceptionType = null;
        String exceptionMessage = null;
        for (Span.Event event : span.getEventsList()) {
            if (!"exception".equals(event.getName())) {
                continue;
            }
            exceptionType = attribute(event.getAttributesList(), "exception.type");
            exceptionMessage = attribute(event.getAttributesList(), "exception.message");
            break;
        }
        String sourceClass = firstNonNull(attribute(span.getAttributesList(), "code.namespace"),
                attribute(span.getAttributesList(), "code.file.path"));
        Integer sourceLine = integerAttribute(span.getAttributesList(), "code.line.number");
        if (sourceLine == null) {
            sourceLine = integerAttribute(span.getAttributesList(), "code.lineno");
        }
        String errorMessage = span.hasStatus() ? span.getStatus().getMessage() : "";
        String terminalMessage = failed ? firstNonBlank(errorMessage, exceptionMessage, "OTLP span failed")
                : "OTLP span completed";
        // An exception event alone does not mark a span FAILED; it can be handled by application code.
        return List.of(
                new ObservationRequest(traceId, spanId, parent, service, operation,
                        FlowStatus.RUNNING, startedAt, "OTLP span started", null, sourceClass, sourceLine),
                new ObservationRequest(traceId, spanId, parent, service, operation,
                        failed ? FlowStatus.FAILED : FlowStatus.SUCCEEDED, endedAt,
                        bounded(terminalMessage, 1000), failed ? bounded(exceptionType, 200) : null,
                        sourceClass, sourceLine)
        );
    }

    private static String serviceName(Resource resource) {
        String value = attribute(resource.getAttributesList(), "service.name");
        return bounded(value == null || value.isBlank() ? "unknown-service" : value, 100);
    }

    private static String attribute(List<KeyValue> attrs, String key) {
        for (KeyValue attribute : attrs) {
            if (key.equals(attribute.getKey())
                    && attribute.getValue().getValueCase() == AnyValue.ValueCase.STRING_VALUE) {
                return attribute.getValue().getStringValue();
            }
        }
        return null;
    }

    private static Integer integerAttribute(List<KeyValue> attrs, String key) {
        for (KeyValue attribute : attrs) {
            if (key.equals(attribute.getKey())
                    && attribute.getValue().getValueCase() == AnyValue.ValueCase.INT_VALUE) {
                long value = attribute.getValue().getIntValue();
                return value >= Integer.MIN_VALUE && value <= Integer.MAX_VALUE ? (int) value : null;
            }
        }
        return null;
    }

    private static String firstNonNull(String a, String b) {
        return a == null ? b : a;
    }

    private static String firstNonBlank(String first, String second, String fallback) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return second != null && !second.isBlank() ? second : fallback;
    }

    private static String bounded(String value, int maxChars) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxChars ? value : value.substring(0, maxChars);
    }

    private static Instant fromNanos(long nanos, Instant fallback) {
        return nanos == 0 ? fallback : Instant.ofEpochSecond(nanos / 1_000_000_000,
                nanos % 1_000_000_000);
    }

    private static String toHex(ByteString id) {
        return HexFormat.of().formatHex(id.toByteArray());
    }
}
