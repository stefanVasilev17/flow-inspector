package com.flowinspector.repository;

import com.flowinspector.domain.Observation;
import com.flowinspector.dto.ObservationRequest;

import java.util.List;
import java.util.Map;

/** Storage contract shared by in-memory and PostgreSQL implementations. */
public interface ObservationRepository {
    Observation save(ObservationRequest request);

    List<Observation> findByTraceId(String traceId);

    Map<String, List<Observation>> findAllGroupedByTrace();
}
