package com.flowinspector.service;

import com.flowinspector.domain.FailureCandidate;
import com.flowinspector.domain.FlowEdge;
import com.flowinspector.domain.FlowNode;
import com.flowinspector.domain.FlowSnapshot;
import com.flowinspector.domain.FlowStatus;
import com.flowinspector.domain.Observation;
import com.flowinspector.domain.TraceSummary;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Derives a trace graph from append-only observations. It does not claim causal proof. */
@Service
public class FlowAnalyzer {
    private static final Comparator<Observation> BY_TIME =
            Comparator.comparing(Observation::occurredAt).thenComparingLong(Observation::sequence);

    public FlowSnapshot analyze(String traceId, List<Observation> raw) {
        if (raw.isEmpty()) {
            throw new IllegalArgumentException("Cannot analyze an empty trace");
        }
        List<Observation> timeline = raw.stream().sorted(BY_TIME).toList();
        Map<String, List<Observation>> byEventId = new LinkedHashMap<>();
        for (Observation observation : timeline) {
            byEventId.computeIfAbsent(observation.eventId(), ignored -> new ArrayList<>())
                    .add(observation);
        }

        List<FlowNode> nodes = new ArrayList<>();
        List<FlowEdge> edges = new ArrayList<>();
        for (Map.Entry<String, List<Observation>> entry : byEventId.entrySet()) {
            List<Observation> transitions = entry.getValue();
            Observation first = transitions.getFirst();
            Observation last = transitions.getLast();
            String parent = transitions.stream().map(Observation::parentEventId)
                    .filter(id -> id != null && !id.isBlank()).findFirst().orElse(null);
            nodes.add(new FlowNode(entry.getKey(), parent, last.service(), last.operation(),
                    last.status(), first.occurredAt(), last.occurredAt(), List.copyOf(transitions)));
            if (parent != null && byEventId.containsKey(parent) && !parent.equals(entry.getKey())) {
                edges.add(new FlowEdge(parent, entry.getKey()));
            }
        }
        FailureCandidate failure = firstFailure(timeline);
        return new FlowSnapshot(traceId, overallStatus(nodes), List.copyOf(nodes),
                List.copyOf(edges), timeline, failure);
    }

    public TraceSummary summary(FlowSnapshot snapshot) {
        List<Observation> timeline = snapshot.timeline();
        Set<String> services = new HashSet<>();
        for (Observation observation : timeline) {
            services.add(observation.service());
        }
        List<String> sortedServices = services.stream().sorted().toList();
        return new TraceSummary(snapshot.traceId(), snapshot.status(),
                timeline.getFirst().occurredAt(), timeline.getLast().occurredAt(),
                timeline.size(), sortedServices,
                snapshot.firstObservedFailure() == null ? null : snapshot.firstObservedFailure().service());
    }

    private static FailureCandidate firstFailure(List<Observation> timeline) {
        return timeline.stream().filter(o -> o.status() == FlowStatus.FAILED).findFirst()
                .map(o -> new FailureCandidate(o.eventId(), o.service(), o.operation(),
                        o.occurredAt(), o.message(), o.exceptionType(), o.sourceClass(), o.sourceLine()))
                .orElse(null);
    }

    private static FlowStatus overallStatus(List<FlowNode> nodes) {
        if (nodes.stream().anyMatch(node -> node.status() == FlowStatus.FAILED)) {
            return FlowStatus.FAILED;
        }
        if (nodes.stream().anyMatch(node -> node.status() == FlowStatus.RUNNING
                || node.status() == FlowStatus.RECEIVED)) {
            return FlowStatus.RUNNING;
        }
        return FlowStatus.SUCCEEDED;
    }
}
