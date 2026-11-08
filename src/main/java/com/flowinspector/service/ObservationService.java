package com.flowinspector.service;

import com.flowinspector.domain.Observation;
import com.flowinspector.dto.ObservationRequest;
import com.flowinspector.repository.ObservationRepository;
import org.springframework.stereotype.Service;

/** Use case: record a transition and notify subscribers after persistence succeeds. */
@Service
public class ObservationService {
    private final ObservationRepository repository;
    private final LiveEventService liveEventService;

    public ObservationService(ObservationRepository repository, LiveEventService liveEventService) {
        this.repository = repository;
        this.liveEventService = liveEventService;
    }

    public Observation record(ObservationRequest request) {
        Observation saved = repository.save(request);
        liveEventService.publish(saved);
        return saved;
    }
}
