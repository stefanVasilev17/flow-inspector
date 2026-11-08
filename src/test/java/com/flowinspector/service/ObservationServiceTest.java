package com.flowinspector.service;

import com.flowinspector.domain.FlowStatus;
import com.flowinspector.domain.Observation;
import com.flowinspector.dto.ObservationRequest;
import com.flowinspector.repository.ObservationRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ObservationServiceTest {
    private final ObservationRepository repository = mock(ObservationRepository.class);
    private final LiveEventService liveEventService = mock(LiveEventService.class);
    private final ObservationService service = new ObservationService(repository, liveEventService);

    @Test
    void persistsBeforePublishingToLiveSubscribers() {
        ObservationRequest request = new ObservationRequest("trace-1", "checkout", null,
                "payment-service", "checkout", FlowStatus.RUNNING, null, "Started", null, null, null);
        Observation saved = new Observation(1, "trace-1", "checkout", null, "payment-service",
                "checkout", FlowStatus.RUNNING, Instant.now(), "Started", null, null, null);
        when(repository.save(request)).thenReturn(saved);

        assertThat(service.record(request)).isEqualTo(saved);

        var inOrder = inOrder(repository, liveEventService);
        inOrder.verify(repository).save(request);
        inOrder.verify(liveEventService).publish(saved);
    }

    @Test
    void doesNotPublishWhenPersistenceFails() {
        ObservationRequest request = new ObservationRequest("trace-1", "checkout", null,
                "payment-service", "checkout", FlowStatus.RUNNING, null, "Started", null, null, null);
        when(repository.save(request)).thenThrow(new IllegalStateException("db down"));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.record(request))
                .isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(liveEventService);
    }
}
