package com.flowinspector.repository;

import com.flowinspector.domain.Observation;
import com.flowinspector.dto.ObservationRequest;

import java.util.List;
import java.util.Map;

/** Persistence contract. Replace the in-memory implementation without changing services. */
public interface ObservationRepository {
    Observation save(ObservationRequest request);

    List<Observation> findByTraceId(String traceId);

    Map<String, List<Observation>> findAllGroupedByTrace();
}
