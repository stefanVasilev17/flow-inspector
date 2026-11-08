package com.flowinspector.repository;

import com.flowinspector.domain.FlowStatus;
import com.flowinspector.dto.ObservationRequest;
import com.flowinspector.service.TraceService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Runs against the PostgreSQL service in GitHub Actions (not required for default local mvn verify). */
@SpringBootTest
@ActiveProfiles("postgres")
@EnabledIfEnvironmentVariable(named = "RUN_POSTGRES_IT", matches = "true")
class PostgresObservationRepositoryIntegrationTest {
    @Autowired
    private ObservationRepository repository;
    @Autowired
    private TraceService traceService;

    @Test
    void persistsTransitionsAndReconstructsFlowFromPostgres() {
        assertThat(repository).isInstanceOf(PostgresObservationRepository.class);
        String traceId = "pg-it-" + UUID.randomUUID();
        Instant now = Instant.now();
        var started = repository.save(new ObservationRequest(traceId, "checkout", null,
                "payment-service", "checkout", FlowStatus.RUNNING, now, "Started", null, null, null));
        var failed = repository.save(new ObservationRequest(traceId, "checkout", null,
                "payment-service", "checkout", FlowStatus.FAILED, now.plusMillis(10),
                "Error", "TimeoutException", "CheckoutService", 42));

        assertThat(failed.sequence()).isGreaterThan(started.sequence());
        assertThat(repository.findByTraceId(traceId)).hasSize(2);
        assertThat(repository.findAllGroupedByTrace()).containsKey(traceId);
        var snapshot = traceService.getTrace(traceId);
        assertThat(snapshot.status()).isEqualTo(FlowStatus.FAILED);
        assertThat(snapshot.firstObservedFailure().exceptionType()).isEqualTo("TimeoutException");
        assertThat(snapshot.timeline()).hasSize(2);
    }
}
