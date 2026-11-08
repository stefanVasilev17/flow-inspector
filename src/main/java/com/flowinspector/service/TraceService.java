package com.flowinspector.service;

import com.flowinspector.domain.FlowSnapshot;
import com.flowinspector.domain.Observation;
import com.flowinspector.domain.TraceSummary;
import com.flowinspector.exception.TraceNotFoundException;
import com.flowinspector.repository.ObservationRepository;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

/** Query use cases: list traces and reconstruct one trace's execution graph. */
@Service
public class TraceService {
    private final ObservationRepository repository;
    private final FlowAnalyzer analyzer;

    public TraceService(ObservationRepository repository, FlowAnalyzer analyzer) {
        this.repository = repository;
        this.analyzer = analyzer;
    }

    public List<TraceSummary> listTraces() {
        return repository.findAllGroupedByTrace().entrySet().stream()
                .map(entry -> analyzer.summary(analyzer.analyze(entry.getKey(), entry.getValue())))
                .sorted(Comparator.comparing(TraceSummary::updatedAt).reversed())
                .toList();
    }

    public FlowSnapshot getTrace(String traceId) {
        List<Observation> observations = repository.findByTraceId(traceId);
        if (observations.isEmpty()) {
            throw new TraceNotFoundException(traceId);
        }
        return analyzer.analyze(traceId, observations);
    }
}
