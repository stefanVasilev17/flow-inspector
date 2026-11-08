package com.flowinspector.repository;

import com.flowinspector.domain.Observation;
import com.flowinspector.dto.ObservationRequest;
import com.flowinspector.exception.ObservationLimitExceededException;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Temporary, bounded and volatile repository for local development only.
 * The lock protects inserts, reads and trace eviction. No HTTP or SSE concerns live here.
 */
@Repository
@Profile("!postgres")
public class InMemoryObservationRepository implements ObservationRepository {
    private static final int MAX_TRACES = 200;
    private static final int MAX_OBSERVATIONS_PER_TRACE = 500;

    private final AtomicLong sequence = new AtomicLong();
    private final Map<String, List<Observation>> traces = new LinkedHashMap<>();

    @Override
    public synchronized Observation save(ObservationRequest request) {
        List<Observation> entries = traces.get(request.traceId());
        if (entries != null && entries.size() >= MAX_OBSERVATIONS_PER_TRACE) {
            throw new ObservationLimitExceededException(MAX_OBSERVATIONS_PER_TRACE);
        }
        if (entries == null) {
            if (traces.size() >= MAX_TRACES) {
                String oldestTraceId = traces.keySet().iterator().next();
                traces.remove(oldestTraceId);
            }
            entries = new ArrayList<>();
            traces.put(request.traceId(), entries);
        }
        Observation observation = new Observation(
                sequence.incrementAndGet(), request.traceId(), request.eventId(),
                request.parentEventId(), request.service(), request.operation(),
                request.status(), request.occurredAt() == null ? Instant.now() : request.occurredAt(),
                request.message(), request.exceptionType(), request.sourceClass(), request.sourceLine()
        );
        entries.add(observation);
        return observation;
    }

    @Override
    public synchronized List<Observation> findByTraceId(String traceId) {
        return List.copyOf(traces.getOrDefault(traceId, List.of()));
    }

    @Override
    public synchronized Map<String, List<Observation>> findAllGroupedByTrace() {
        Map<String, List<Observation>> copy = new LinkedHashMap<>();
        traces.forEach((key, observations) -> copy.put(key, List.copyOf(observations)));
        return copy;
    }
}
