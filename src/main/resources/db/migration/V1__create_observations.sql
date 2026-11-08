-- Append-only state transitions. Do not store payloads, headers, or sensitive user identifiers.
CREATE TABLE flow_observation (
    sequence BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    trace_id VARCHAR(128) NOT NULL,
    event_id VARCHAR(128) NOT NULL,
    parent_event_id VARCHAR(128),
    service VARCHAR(100) NOT NULL,
    operation VARCHAR(160) NOT NULL,
    status VARCHAR(16) NOT NULL CHECK (status IN ('RECEIVED','RUNNING','SUCCEEDED','FAILED','SKIPPED')),
    occurred_at TIMESTAMPTZ NOT NULL,
    message VARCHAR(1000),
    exception_type VARCHAR(200),
    source_class VARCHAR(240),
    source_line INTEGER,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_flow_observation_trace_sequence ON flow_observation (trace_id, sequence);
CREATE INDEX idx_flow_observation_trace_time ON flow_observation (trace_id, occurred_at, sequence);
CREATE INDEX idx_flow_observation_recent_traces ON flow_observation (sequence DESC);
