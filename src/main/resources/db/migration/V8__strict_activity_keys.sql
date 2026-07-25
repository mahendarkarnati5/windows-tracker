ALTER TABLE process_activity
    ADD COLUMN natural_key VARCHAR(64);

ALTER TABLE active_window_activity
    ADD COLUMN pid BIGINT;
ALTER TABLE active_window_activity
    ADD COLUMN process_name VARCHAR(512);
ALTER TABLE active_window_activity
    ADD COLUMN natural_key VARCHAR(64);

ALTER TABLE idle_activity
    ADD COLUMN natural_key VARCHAR(64);

ALTER TABLE device_session
    ADD COLUMN natural_key VARCHAR(64);

-- Only new/updated rows receive a natural key. Existing historical rows remain NULL,
-- so this migration is fast and does not scan/rewrite large activity tables.
CREATE UNIQUE INDEX ux_process_activity_natural
    ON process_activity (device_id, natural_key);
CREATE UNIQUE INDEX ux_active_window_activity_natural
    ON active_window_activity (device_id, natural_key);
CREATE UNIQUE INDEX ux_idle_activity_natural
    ON idle_activity (device_id, natural_key);
CREATE UNIQUE INDEX ux_device_session_natural
    ON device_session (device_id, natural_key);

CREATE INDEX ix_process_activity_open_lookup
    ON process_activity (device_id, pid, start_time, end_time);
CREATE INDEX ix_active_window_open_lookup
    ON active_window_activity (device_id, pid, start_time, end_time);
CREATE INDEX ix_idle_activity_open_lookup
    ON idle_activity (device_id, idle_start, idle_end);
CREATE INDEX ix_device_session_open_lookup
    ON device_session (device_id, startup_time, shutdown_time);
CREATE INDEX ix_devices_presence
    ON devices (online, status, last_seen);

-- Dashboard list/current-row queries. These keep status and latest-row lookups
-- index-backed without any runtime duplicate-repair scan.
CREATE INDEX ix_process_activity_device_status_start_id
    ON process_activity (device_id, status, start_time, id);
CREATE INDEX ix_active_window_device_status_start_id
    ON active_window_activity (device_id, status, start_time, id);
CREATE INDEX ix_idle_activity_device_status_start_id
    ON idle_activity (device_id, status, idle_start, id);
CREATE INDEX ix_device_session_device_status_start_id
    ON device_session (device_id, status, startup_time, id);
