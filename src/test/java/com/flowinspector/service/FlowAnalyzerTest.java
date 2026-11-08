package com.flowinspector.service;

import com.flowinspector.domain.FlowSnapshot;
import com.flowinspector.domain.FlowStatus;
import com.flowinspector.domain.Observation;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FlowAnalyzerTest {
    private final FlowAnalyzer analyzer = new FlowAnalyzer();
    private final Instant base = Instant.parse("2026-11-08T10:00:00Z");

    @Test
    void groupsStateTransitionsAndFindsFirstObservedFailure() {
        // Observations arrive out of order: occurrence timestamp defines the timeline.
        List<Observation> observations = List.of(
                observation(4, "risk", "checkout", FlowStatus.FAILED, 400, "RiskEvaluationException"),
                observation(1, "checkout", null, FlowStatus.RUNNING, 0, null),
                observation(3, "provider", "risk", FlowStatus.FAILED, 300, "SocketTimeoutException"),
                observation(2, "risk", "checkout", FlowStatus.RUNNING, 100, null),
                observation(5, "checkout", null, FlowStatus.FAILED, 500, "CheckoutException")
        );

        FlowSnapshot snapshot = analyzer.analyze("trace-1", observations);

        assertThat(snapshot.nodes()).hasSize(3);
        assertThat(snapshot.edges()).hasSize(2);
        assertThat(snapshot.edges()).extracting(edge -> edge.source() + "->" + edge.target())
                .containsExactlyInAnyOrder("checkout->risk", "risk->provider");
        assertThat(snapshot.timeline()).extracting(Observation::eventId)
                .containsExactly("checkout", "risk", "provider", "risk", "checkout");
        assertThat(snapshot.firstObservedFailure().eventId()).isEqualTo("provider");
        assertThat(snapshot.firstObservedFailure().exceptionType()).isEqualTo("SocketTimeoutException");
        assertThat(snapshot.status()).isEqualTo(FlowStatus.FAILED);
        assertThat(snapshot.nodes().stream().filter(node -> node.id().equals("risk")).findFirst().orElseThrow()
                .transitions()).hasSize(2);
    }

    @Test
    void successfulTraceWithTwoCompletedOperationsIsSuccessful() {
        FlowSnapshot snapshot = analyzer.analyze("trace-1", List.of(
                observation(1, "checkout", null, FlowStatus.SUCCEEDED, 100, null),
                observation(2, "device", "checkout", FlowStatus.SUCCEEDED, 200, null)
        ));

        assertThat(snapshot.status()).isEqualTo(FlowStatus.SUCCEEDED);
        assertThat(snapshot.firstObservedFailure()).isNull();
    }

    @Test
    void unknownParentDoesNotInventGraphEdge() {
        FlowSnapshot snapshot = analyzer.analyze("trace-1", List.of(
                observation(1, "provider", "unknown", FlowStatus.RUNNING, 100, null)
        ));

        assertThat(snapshot.edges()).isEmpty();
        assertThat(snapshot.status()).isEqualTo(FlowStatus.RUNNING);
    }

    private Observation observation(long sequence, String id, String parent, FlowStatus status,
                                    long deltaMs, String exception) {
        return new Observation(sequence, "trace-1", id, parent, "service-" + id, "op-" + id,
                status, base.plusMillis(deltaMs), "test", exception, "TestService", 42);
    }
}
