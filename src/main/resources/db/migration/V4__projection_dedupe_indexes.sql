-- Supports duplicate-safe projection lookup and periodic repair without scanning
-- every activity table. These are intentionally non-unique because older
-- deployments may already contain duplicate rows; startup repair merges them.
CREATE INDEX ix_process_activity_device_pid_start
    ON process_activity (device_id, pid, start_time);

CREATE INDEX ix_process_activity_device_pid_status_start
    ON process_activity (device_id, pid, status, start_time);

CREATE INDEX ix_active_window_device_start
    ON active_window_activity (device_id, start_time);

CREATE INDEX ix_idle_activity_device_start
    ON idle_activity (device_id, idle_start);

CREATE INDEX ix_device_session_device_start
    ON device_session (device_id, startup_time);

CREATE INDEX ix_agent_activity_legacy_projection
    ON agent_activities (kind, legacy_record_id);
