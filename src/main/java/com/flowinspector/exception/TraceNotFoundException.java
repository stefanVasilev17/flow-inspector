package com.flowinspector.exception;

public class TraceNotFoundException extends RuntimeException {
    public TraceNotFoundException(String traceId) {
        super("Unknown traceId: " + traceId);
    }
}
