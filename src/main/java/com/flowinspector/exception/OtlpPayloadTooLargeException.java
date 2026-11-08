package com.flowinspector.exception;

public class OtlpPayloadTooLargeException extends RuntimeException {
    public OtlpPayloadTooLargeException(String message) {
        super(message);
    }
}
