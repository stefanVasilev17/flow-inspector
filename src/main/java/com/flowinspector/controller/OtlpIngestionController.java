package com.flowinspector.controller;

import com.flowinspector.service.OtlpTraceService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/** Native OTLP/HTTP trace receiver. Java agents may POST directly to /v1/traces. */
@RestController
public class OtlpIngestionController {
    private static final MediaType PROTOBUF = MediaType.parseMediaType("application/x-protobuf");
    private final OtlpTraceService service;

    public OtlpIngestionController(OtlpTraceService service) {
        this.service = service;
    }

    @PostMapping(path = "/v1/traces", consumes = {"application/x-protobuf", "application/json"})
    public ResponseEntity<byte[]> export(@RequestBody byte[] body,
                                         @RequestHeader(HttpHeaders.CONTENT_TYPE) String contentType,
                                         @RequestHeader(value = HttpHeaders.CONTENT_ENCODING, required = false)
                                         String contentEncoding) {
        MediaType received = MediaType.parseMediaType(contentType);
        boolean json = MediaType.APPLICATION_JSON.isCompatibleWith(received);
        service.ingest(body, json ? "application/json" : "application/x-protobuf", contentEncoding);
        // OTLP ExportTraceServiceResponse is an empty protobuf message when fully accepted.
        return ResponseEntity.ok().contentType(json ? MediaType.APPLICATION_JSON : PROTOBUF)
                .body(json ? "{}".getBytes(java.nio.charset.StandardCharsets.UTF_8) : new byte[0]);
    }
}
