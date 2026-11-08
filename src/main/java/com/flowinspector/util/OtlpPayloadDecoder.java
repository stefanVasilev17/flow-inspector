package com.flowinspector.util;

import com.flowinspector.exception.InvalidOtlpPayloadException;
import com.flowinspector.exception.OtlpPayloadTooLargeException;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.util.JsonFormat;
import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceRequest;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.zip.GZIPInputStream;

/** Parses the standard OTLP/HTTP wire formats without interpreting business-state events. */
@Component
public class OtlpPayloadDecoder {
    public static final int MAX_BODY_BYTES = 4 * 1024 * 1024;

    public ExportTraceServiceRequest decode(byte[] body, String contentType, String contentEncoding) {
        if (body.length > MAX_BODY_BYTES) {
            throw new OtlpPayloadTooLargeException("OTLP payload must be at most 4 MiB");
        }
        byte[] decoded = decompress(body, contentEncoding);
        if ("application/x-protobuf".equals(contentType)) {
            try {
                return ExportTraceServiceRequest.parseFrom(decoded);
            } catch (InvalidProtocolBufferException exception) {
                throw new InvalidOtlpPayloadException("Malformed OTLP protobuf request");
            }
        }
        if ("application/json".equals(contentType)) {
            try {
                ExportTraceServiceRequest.Builder request = ExportTraceServiceRequest.newBuilder();
                JsonFormat.parser().merge(new String(decoded, StandardCharsets.UTF_8), request);
                return request.build();
            } catch (InvalidProtocolBufferException exception) {
                throw new InvalidOtlpPayloadException("Malformed OTLP JSON request");
            }
        }
        throw new InvalidOtlpPayloadException("Only application/x-protobuf and application/json are supported");
    }

    private byte[] decompress(byte[] body, String contentEncoding) {
        if (contentEncoding == null || contentEncoding.isBlank()
                || "identity".equalsIgnoreCase(contentEncoding)) {
            return body;
        }
        if (!"gzip".equals(contentEncoding.toLowerCase(Locale.ROOT).trim())) {
            throw new InvalidOtlpPayloadException("Unsupported OTLP Content-Encoding");
        }
        try (GZIPInputStream input = new GZIPInputStream(new ByteArrayInputStream(body));
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                if (output.size() + read > MAX_BODY_BYTES) {
                    throw new OtlpPayloadTooLargeException("Decompressed OTLP payload exceeds 4 MiB");
                }
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        } catch (IOException exception) {
            throw new InvalidOtlpPayloadException("Malformed gzipped OTLP payload");
        }
    }
}
