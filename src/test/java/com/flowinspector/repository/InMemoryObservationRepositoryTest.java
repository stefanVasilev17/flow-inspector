package com.flowinspector.repository;

import com.flowinspector.domain.FlowStatus;
import com.flowinspector.domain.Observation;
import com.flowinspector.dto.ObservationRequest;
import com.flowinspector.exception.ObservationLimitExceededException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InMemoryObservationRepositoryTest {
    private final InMemoryObservationRepository repository = new InMemoryObservationRepository();

    @Test
    void savesImmutableTransitionsForTheSameTrace() {
        Observation received = repository.save(request("trace-1", FlowStatus.RECEIVED));
        Observation failed = repository.save(request("trace-1", FlowStatus.FAILED));

        List<Observation> observations = repository.findByTraceId("trace-1");
        assertThat(observations).containsExactly(received, failed);
        assertThat(failed.sequence()).isGreaterThan(received.sequence());
        assertThat(observations).allSatisfy(observation -> assertThat(observation.occurredAt()).isNotNull());
        assertThatThrownBy(() -> observations.clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsMoreThanFiveHundredObservationsPerTrace() {
        for (int index = 0; index < 500; index++) {
            repository.save(request("trace-1", FlowStatus.RUNNING));
        }

        assertThatThrownBy(() -> repository.save(request("trace-1", FlowStatus.FAILED)))
                .isInstanceOf(ObservationLimitExceededException.class);
        assertThat(repository.findByTraceId("trace-1")).hasSize(500);
    }

    @Test
    void evictsOldestTraceWhenCapacityReached() {
        for (int index = 0; index < 200; index++) {
            repository.save(request("trace-" + index, FlowStatus.RECEIVED));
        }
        repository.save(request("trace-200", FlowStatus.RECEIVED));

        assertThat(repository.findAllGroupedByTrace()).hasSize(200);
        assertThat(repository.findByTraceId("trace-0")).isEmpty();
        assertThat(repository.findByTraceId("trace-200")).hasSize(1);
    }

    private ObservationRequest request(String traceId, FlowStatus status) {
        return new ObservationRequest(traceId, "checkout", null, "payment-service", "checkout",
                status, null, "test", null, "CheckoutService", 42);
    }
}
