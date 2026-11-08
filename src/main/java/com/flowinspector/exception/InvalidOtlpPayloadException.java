package com.flowinspector.exception;

/** Invalid OTLP protocol payload; client can fix or reduce the exported batch. */
public class InvalidOtlpPayloadException extends RuntimeException {
    public InvalidOtlpPayloadException(String message) {
        super(message);
    }
}
