package com.flowinspector.domain;

import java.util.List;

public record FlowSnapshot(
        String traceId,
        FlowStatus status,
        List<FlowNode> nodes,
        List<FlowEdge> edges,
        List<Observation> timeline,
        FailureCandidate firstObservedFailure
) { }
