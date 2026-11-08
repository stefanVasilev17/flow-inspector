package com.flowinspector.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Maps application errors to HTTP responses; service and repository have no HTTP dependencies. */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(TraceNotFoundException.class)
    public ProblemDetail handleTraceNotFound(TraceNotFoundException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
        problem.setTitle("Trace not found");
        return problem;
    }

    @ExceptionHandler(InvalidOtlpPayloadException.class)
    public ProblemDetail handleInvalidOtlp(InvalidOtlpPayloadException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
        problem.setTitle("Invalid OTLP trace request");
        return problem;
    }

    @ExceptionHandler(OtlpPayloadTooLargeException.class)
    public ProblemDetail handleOtlpLimit(OtlpPayloadTooLargeException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.PAYLOAD_TOO_LARGE,
                exception.getMessage());
        problem.setTitle("OTLP batch limit exceeded");
        return problem;
    }

    @ExceptionHandler(ObservationLimitExceededException.class)
    public ProblemDetail handleObservationLimit(ObservationLimitExceededException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.PAYLOAD_TOO_LARGE,
                exception.getMessage());
        problem.setTitle("Trace observation limit exceeded");
        return problem;
    }
}
