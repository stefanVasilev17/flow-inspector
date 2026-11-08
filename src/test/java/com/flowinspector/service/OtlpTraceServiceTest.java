package com.flowinspector.service;

import com.flowinspector.domain.FlowStatus;
import com.flowinspector.dto.ObservationRequest;
import com.flowinspector.exception.InvalidOtlpPayloadException;
import com.flowinspector.util.OtlpPayloadDecoder;
import com.google.protobuf.ByteString;
import com.google.protobuf.util.JsonFormat;
import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceRequest;
import io.opentelemetry.proto.common.v1.AnyValue;
import io.opentelemetry.proto.common.v1.KeyValue;
import io.opentelemetry.proto.resource.v1.Resource;
import io.opentelemetry.proto.trace.v1.ResourceSpans;
import io.opentelemetry.proto.trace.v1.ScopeSpans;
import io.opentelemetry.proto.trace.v1.Span;
import io.opentelemetry.proto.trace.v1.Status;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class OtlpTraceServiceTest {
    private final ObservationService observationService = mock(ObservationService.class);
    private final OtlpTraceService service = new OtlpTraceService(new OtlpPayloadDecoder(), observationService);

    @Test
    void mapsRealProtobufErrorSpanIntoTwoTransitions() {
        Span span = span().setStatus(Status.newBuilder().setCode(Status.StatusCode.STATUS_CODE_ERROR)
                .setMessage("provider timeout"))
                .addEvents(Span.Event.newBuilder().setName("exception")
                        .addAttributes(textAttribute("exception.type", "SocketTimeoutException")))
                .addAttributes(textAttribute("code.namespace", "com.example.FraudClient"))
                .addAttributes(KeyValue.newBuilder().setKey("code.line.number")
                        .setValue(AnyValue.newBuilder().setIntValue(142)))
                .build();

        assertThat(service.ingest(request(span).toByteArray(), "application/x-protobuf", null)).isEqualTo(1);

        var captor = org.mockito.ArgumentCaptor.forClass(ObservationRequest.class);
        verify(observationService, times(2)).record(captor.capture());
        var events = captor.getAllValues();
        assertThat(events).extracting(ObservationRequest::status)
                .containsExactly(FlowStatus.RUNNING, FlowStatus.FAILED);
        assertThat(events.get(0).traceId()).isEqualTo("00112233445566778899aabbccddeeff");
        assertThat(events.get(0).eventId()).isEqualTo("aabbccddeeff0011");
        assertThat(events.get(0).parentEventId()).isEqualTo("1111222233334444");
        assertThat(events.get(1).service()).isEqualTo("fraud-provider");
        assertThat(events.get(1).exceptionType()).isEqualTo("SocketTimeoutException");
        assertThat(events.get(1).sourceClass()).isEqualTo("com.example.FraudClient");
        assertThat(events.get(1).sourceLine()).isEqualTo(142);
        assertThat(events.get(1).message()).isEqualTo("provider timeout");
        assertThat(events.get(1).occurredAt()).isAfter(events.get(0).occurredAt());
    }

    @Test
    void acceptsOtlpJsonProtobufMapping() throws Exception {
        var request = request(span().setStatus(Status.newBuilder()
                .setCode(Status.StatusCode.STATUS_CODE_OK)).build());
        byte[] json = JsonFormat.printer().print(request).getBytes(StandardCharsets.UTF_8);

        assertThat(service.ingest(json, "application/json", null)).isEqualTo(1);
        var captor = org.mockito.ArgumentCaptor.forClass(ObservationRequest.class);
        verify(observationService, times(2)).record(captor.capture());
        assertThat(captor.getAllValues().get(1).status()).isEqualTo(FlowStatus.SUCCEEDED);
    }

    @Test
    void rejectsInvalidSpanBeforePersistingAnyEventsInBatch() {
        Span goodSpan = span().build();
        Span invalidSpan = span().clearSpanId().build();
        byte[] body = request(goodSpan, invalidSpan).toByteArray();

        assertThatThrownBy(() -> service.ingest(body, "application/x-protobuf", null))
                .isInstanceOf(InvalidOtlpPayloadException.class);
        verifyNoInteractions(observationService);
    }

    @Test
    void recordedExceptionDoesNotImplyFailedStatusWithoutErrorStatus() {
        Span span = span().addEvents(Span.Event.newBuilder().setName("exception")
                .addAttributes(textAttribute("exception.type", "HandledException"))).build();
        service.ingest(request(span).toByteArray(), "application/x-protobuf", null);

        var captor = org.mockito.ArgumentCaptor.forClass(ObservationRequest.class);
        verify(observationService, times(2)).record(captor.capture());
        assertThat(captor.getAllValues().get(1).status()).isEqualTo(FlowStatus.SUCCEEDED);
        assertThat(captor.getAllValues().get(1).exceptionType()).isNull();
    }

    private static Span.Builder span() {
        return Span.newBuilder()
                .setTraceId(ByteString.copyFrom(HexFormat.of().parseHex("00112233445566778899aabbccddeeff")))
                .setSpanId(ByteString.copyFrom(HexFormat.of().parseHex("aabbccddeeff0011")))
                .setParentSpanId(ByteString.copyFrom(HexFormat.of().parseHex("1111222233334444")))
                .setName("FraudClient.getRiskScore")
                .setStartTimeUnixNano(1_783_500_000_000_000_000L)
                .setEndTimeUnixNano(1_783_500_000_025_000_000L);
    }

    private static KeyValue textAttribute(String key, String value) {
        return KeyValue.newBuilder().setKey(key).setValue(AnyValue.newBuilder().setStringValue(value)).build();
    }

    private static ExportTraceServiceRequest request(Span... spans) {
        Resource resource = Resource.newBuilder().addAttributes(textAttribute("service.name", "fraud-provider"))
                .build();
        var scope = ScopeSpans.newBuilder().addAllSpans(java.util.List.of(spans)).build();
        return ExportTraceServiceRequest.newBuilder().addResourceSpans(ResourceSpans.newBuilder()
                .setResource(resource).addScopeSpans(scope)).build();
    }
}
