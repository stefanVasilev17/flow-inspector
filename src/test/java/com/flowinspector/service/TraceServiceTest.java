package com.flowinspector.service;

import com.flowinspector.domain.FlowStatus;
import com.flowinspector.domain.Observation;
import com.flowinspector.exception.TraceNotFoundException;
import com.flowinspector.repository.ObservationRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class TraceServiceTest {
    private final ObservationRepository repository = mock(ObservationRepository.class);
    private final TraceService service = new TraceService(repository, new FlowAnalyzer());

    @Test
    void rejectsUnknownTrace() {
        when(repository.findByTraceId("missing")).thenReturn(List.of());

        assertThatThrownBy(() -> service.getTrace("missing"))
                .isInstanceOf(TraceNotFoundException.class)
                .hasMessageContaining("missing");
    }

    @Test
    void readsAndAnalyzesTraceWithoutControllerLogic() {
        Observation observation = new Observation(1L, "trace-1", "checkout", null, "payment-service",
                "checkout", FlowStatus.SUCCEEDED, Instant.parse("2026-11-08T10:00:00Z"),
                "Completed", null, null, null);
        when(repository.findByTraceId("trace-1")).thenReturn(List.of(observation));
        when(repository.findAllGroupedByTrace()).thenReturn(Map.of("trace-1", List.of(observation)));

        assertThat(service.getTrace("trace-1").status()).isEqualTo(FlowStatus.SUCCEEDED);
        assertThat(service.listTraces()).singleElement().satisfies(summary -> {
            assertThat(summary.traceId()).isEqualTo("trace-1");
            assertThat(summary.observationCount()).isEqualTo(1);
        });
    }
}
