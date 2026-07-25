CREATE INDEX IF NOT EXISTS ix_process_activity_open_lookup
    ON process_activity (device_id, pid, start_time, end_time);
CREATE INDEX IF NOT EXISTS ix_active_window_open_lookup
    ON active_window_activity (device_id, pid, start_time, end_time);
CREATE INDEX IF NOT EXISTS ix_idle_activity_open_lookup
    ON idle_activity (device_id, idle_start, idle_end);
CREATE INDEX IF NOT EXISTS ix_device_session_open_lookup
    ON device_session (device_id, startup_time, shutdown_time);
CREATE INDEX IF NOT EXISTS ix_devices_presence
    ON devices (online, status, last_seen);
