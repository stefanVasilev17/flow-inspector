package com.flowinspector.exception;

public class ObservationLimitExceededException extends RuntimeException {
    public ObservationLimitExceededException(int limit) {
        super("Trace observation limit exceeded (" + limit + ")");
    }
}
