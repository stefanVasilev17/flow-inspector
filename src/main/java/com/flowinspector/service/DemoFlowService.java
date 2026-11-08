package com.flowinspector.service;

import com.flowinspector.domain.FlowStatus;
import com.flowinspector.dto.ObservationRequest;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** Generates a synthetic execution flow; the HTTP controller stays free of business logic. */
@Service
public class DemoFlowService {
    private final ObservationService observationService;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(task -> {
        Thread thread = new Thread(task, "demo-flow-generator");
        thread.setDaemon(true);
        return thread;
    });

    public DemoFlowService(ObservationService observationService) {
        this.observationService = observationService;
    }

    public String generate() {
        String traceId = "demo-" + UUID.randomUUID().toString().substring(0, 8);
        write(traceId, "checkout", null, "payment-service", "checkout", FlowStatus.RECEIVED,
                "Request accepted", null, "CheckoutController", 42);
        schedule(traceId, 400, "checkout", null, "payment-service", "checkout", FlowStatus.RUNNING,
                "Starting checkout", null, "CheckoutService", 71);
        schedule(traceId, 800, "risk", "checkout", "risk-rule-engine", "evaluateRisk", FlowStatus.RUNNING,
                "Evaluating rules", null, "RiskEvaluator", 95);
        schedule(traceId, 1200, "fingerprint", "risk", "fingerprint-service", "loadDevice", FlowStatus.RUNNING,
                "Fetching device metadata", null, "FingerprintClient", 58);
        schedule(traceId, 1600, "fingerprint", "risk", "fingerprint-service", "loadDevice", FlowStatus.SUCCEEDED,
                "Device fingerprint loaded", null, "FingerprintClient", 62);
        schedule(traceId, 2000, "fraud-provider", "risk", "fraud-provider-adapter", "getRiskScore",
                FlowStatus.RUNNING, "Calling provider", null, "FraudClient", 134);
        schedule(traceId, 2600, "fraud-provider", "risk", "fraud-provider-adapter", "getRiskScore",
                FlowStatus.FAILED, "External provider did not respond before deadline",
                "SocketTimeoutException", "FraudClient", 142);
        schedule(traceId, 3000, "risk", "checkout", "risk-rule-engine", "evaluateRisk", FlowStatus.FAILED,
                "Risk score unavailable because provider timed out",
                "RiskEvaluationException", "RiskEvaluator", 115);
        schedule(traceId, 3400, "checkout", null, "payment-service", "checkout", FlowStatus.FAILED,
                "Checkout aborted due to risk evaluation failure",
                "CheckoutException", "CheckoutService", 89);
        return traceId;
    }

    private void schedule(String traceId, long delayMillis, String eventId, String parent, String service,
                          String operation, FlowStatus status, String message, String exceptionType,
                          String sourceClass, Integer line) {
        scheduler.schedule(() -> write(traceId, eventId, parent, service, operation, status,
                message, exceptionType, sourceClass, line), delayMillis, TimeUnit.MILLISECONDS);
    }

    private void write(String traceId, String eventId, String parent, String service,
                       String operation, FlowStatus status, String message,
                       String exceptionType, String sourceClass, Integer line) {
        observationService.record(new ObservationRequest(traceId, eventId, parent, service, operation,
                status, Instant.now(), message, exceptionType, sourceClass, line));
    }

    @PreDestroy
    public void shutdown() {
        scheduler.shutdownNow();
    }
}
