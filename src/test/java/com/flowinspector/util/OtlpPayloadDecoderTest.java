package com.flowinspector.util;

import com.flowinspector.exception.InvalidOtlpPayloadException;
import com.flowinspector.exception.OtlpPayloadTooLargeException;
import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceRequest;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.util.zip.GZIPOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OtlpPayloadDecoderTest {
    private final OtlpPayloadDecoder decoder = new OtlpPayloadDecoder();

    @Test
    void handlesGzipProtobufPayload() throws Exception {
        var request = ExportTraceServiceRequest.newBuilder().build();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(output)) {
            gzip.write(request.toByteArray());
        }

        assertThat(decoder.decode(output.toByteArray(), "application/x-protobuf", "gzip"))
                .isEqualTo(request);
    }

    @Test
    void rejectsOversizePayload() {
        assertThatThrownBy(() -> decoder.decode(new byte[OtlpPayloadDecoder.MAX_BODY_BYTES + 1],
                "application/x-protobuf", null)).isInstanceOf(OtlpPayloadTooLargeException.class);
    }

    @Test
    void rejectsUnsupportedEncoding() {
        assertThatThrownBy(() -> decoder.decode(new byte[0], "application/x-protobuf", "br"))
                .isInstanceOf(InvalidOtlpPayloadException.class);
    }
}
