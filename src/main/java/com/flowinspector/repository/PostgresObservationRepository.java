package com.flowinspector.repository;

import com.flowinspector.domain.FlowStatus;
import com.flowinspector.domain.Observation;
import com.flowinspector.dto.ObservationRequest;
import com.flowinspector.exception.ObservationLimitExceededException;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** PostgreSQL implementation. All state changes are inserts; none overwrite prior events. */
@Repository
@Profile("postgres")
public class PostgresObservationRepository implements ObservationRepository {
    private static final int MAX_OBSERVATIONS_PER_TRACE = 500;
    private static final int MAX_LISTED_TRACES = 200;
    private static final RowMapper<Observation> OBSERVATION_MAPPER = PostgresObservationRepository::mapRow;

    private final JdbcTemplate jdbc;

    public PostgresObservationRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public Observation save(ObservationRequest request) {
        // The per-trace transaction lock prevents concurrent inserts from bypassing the size limit.
        jdbc.query("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))",
                (org.springframework.jdbc.core.RowCallbackHandler) rs -> { }, request.traceId());
        Long count = jdbc.queryForObject("SELECT count(*) FROM flow_observation WHERE trace_id = ?", Long.class,
                request.traceId());
        if (count != null && count >= MAX_OBSERVATIONS_PER_TRACE) {
            throw new ObservationLimitExceededException(MAX_OBSERVATIONS_PER_TRACE);
        }
        Instant occurredAt = request.occurredAt() == null ? Instant.now() : request.occurredAt();
        return jdbc.queryForObject("""
                INSERT INTO flow_observation
                    (trace_id, event_id, parent_event_id, service, operation, status, occurred_at,
                     message, exception_type, source_class, source_line)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                RETURNING sequence, trace_id, event_id, parent_event_id, service, operation, status,
                          occurred_at, message, exception_type, source_class, source_line
                """, OBSERVATION_MAPPER,
                request.traceId(), request.eventId(), request.parentEventId(), request.service(),
                request.operation(), request.status().name(), Timestamp.from(occurredAt),
                request.message(), request.exceptionType(), request.sourceClass(), request.sourceLine());
    }

    @Override
    @Transactional(readOnly = true)
    public List<Observation> findByTraceId(String traceId) {
        return jdbc.query("""
                SELECT sequence, trace_id, event_id, parent_event_id, service, operation, status,
                       occurred_at, message, exception_type, source_class, source_line
                FROM flow_observation WHERE trace_id = ? ORDER BY sequence
                """, OBSERVATION_MAPPER, traceId);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, List<Observation>> findAllGroupedByTrace() {
        List<String> traceIds = jdbc.queryForList("""
                SELECT trace_id FROM flow_observation
                GROUP BY trace_id ORDER BY max(sequence) DESC LIMIT ?
                """, String.class, MAX_LISTED_TRACES);
        Map<String, List<Observation>> result = new LinkedHashMap<>();
        for (String traceId : traceIds) {
            result.put(traceId, findByTraceId(traceId));
        }
        return result;
    }

    private static Observation mapRow(ResultSet rs, int rowIndex) throws SQLException {
        Timestamp timestamp = rs.getTimestamp("occurred_at");
        Integer sourceLine = (Integer) rs.getObject("source_line");
        return new Observation(rs.getLong("sequence"), rs.getString("trace_id"), rs.getString("event_id"),
                rs.getString("parent_event_id"), rs.getString("service"), rs.getString("operation"),
                FlowStatus.valueOf(rs.getString("status")), timestamp.toInstant(), rs.getString("message"),
                rs.getString("exception_type"), rs.getString("source_class"), sourceLine);
    }
}
