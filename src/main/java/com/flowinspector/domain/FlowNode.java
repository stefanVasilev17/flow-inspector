package com.flowinspector.domain;

import java.time.Instant;
import java.util.List;

public record FlowNode(
        String id,
        String parentId,
        String service,
        String operation,
        FlowStatus status,
        Instant firstSeen,
        Instant lastSeen,
        List<Observation> transitions
) { }
