ALTER TABLE agent_devices
    ADD COLUMN current_session_uuid VARCHAR(36);

ALTER TABLE agent_devices
    ADD COLUMN current_session_started_at DATETIME(6);

ALTER TABLE agent_devices
    ADD COLUMN current_session_sequence BIGINT;

ALTER TABLE agent_devices
    ADD COLUMN lifecycle_state VARCHAR(32);

ALTER TABLE agent_devices
    ADD COLUMN last_lifecycle_at DATETIME(6);

UPDATE agent_devices
SET lifecycle_state = 'ONLINE',
    last_lifecycle_at = COALESCE(last_seen_at, updated_at)
WHERE lifecycle_state IS NULL;

CREATE INDEX ix_agent_devices_lifecycle_seen
    ON agent_devices (lifecycle_state, last_seen_at);
