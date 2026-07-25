CREATE INDEX IF NOT EXISTS ix_process_activity_device_status_start_id
    ON process_activity (device_id, status, start_time, id);
CREATE INDEX IF NOT EXISTS ix_active_window_device_status_start_id
    ON active_window_activity (device_id, status, start_time, id);
CREATE INDEX IF NOT EXISTS ix_idle_activity_device_status_start_id
    ON idle_activity (device_id, status, idle_start, id);
CREATE INDEX IF NOT EXISTS ix_device_session_device_status_start_id
    ON device_session (device_id, status, startup_time, id);
