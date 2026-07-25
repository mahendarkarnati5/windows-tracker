-- Replay-safe recovery for a v2.3.1 deployment that may have completed DDL
-- before its Flyway schema-history row could be written.
ALTER TABLE process_activity
    ADD COLUMN IF NOT EXISTS natural_key VARCHAR(64);

ALTER TABLE active_window_activity
    ADD COLUMN IF NOT EXISTS pid BIGINT;
ALTER TABLE active_window_activity
    ADD COLUMN IF NOT EXISTS process_name VARCHAR(512);
ALTER TABLE active_window_activity
    ADD COLUMN IF NOT EXISTS natural_key VARCHAR(64);

ALTER TABLE idle_activity
    ADD COLUMN IF NOT EXISTS natural_key VARCHAR(64);

ALTER TABLE device_session
    ADD COLUMN IF NOT EXISTS natural_key VARCHAR(64);
