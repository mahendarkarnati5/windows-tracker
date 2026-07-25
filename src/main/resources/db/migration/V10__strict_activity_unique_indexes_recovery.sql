-- Existing historical rows retain NULL natural keys. MySQL/TiDB unique indexes
-- allow multiple NULL values, while all new keyed rows remain duplicate-safe.
CREATE UNIQUE INDEX IF NOT EXISTS ux_process_activity_natural
    ON process_activity (device_id, natural_key);
CREATE UNIQUE INDEX IF NOT EXISTS ux_active_window_activity_natural
    ON active_window_activity (device_id, natural_key);
CREATE UNIQUE INDEX IF NOT EXISTS ux_idle_activity_natural
    ON idle_activity (device_id, natural_key);
CREATE UNIQUE INDEX IF NOT EXISTS ux_device_session_natural
    ON device_session (device_id, natural_key);
