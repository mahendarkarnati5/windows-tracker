CREATE TABLE IF NOT EXISTS users (
    id BIGINT NOT NULL AUTO_INCREMENT,
    username VARCHAR(255) NOT NULL,
    password VARCHAR(255),
    role VARCHAR(64) NOT NULL,
    created_at DATETIME(6),
    PRIMARY KEY (id),
    CONSTRAINT ux_users_username UNIQUE (username)
);

CREATE TABLE IF NOT EXISTS devices (
    id BIGINT NOT NULL AUTO_INCREMENT,
    mac_address VARCHAR(255),
    machine_name VARCHAR(255),
    os_name VARCHAR(255),
    last_ip_address VARCHAR(255),
    last_seen DATETIME(6),
    status VARCHAR(64),
    uninstalled_at DATETIME(6),
    online BOOLEAN NOT NULL DEFAULT FALSE,
    created_at DATETIME(6),
    user_id BIGINT,
    PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS process_activity (
    id BIGINT NOT NULL AUTO_INCREMENT,
    pid BIGINT,
    process_name VARCHAR(255),
    start_time DATETIME(6),
    end_time DATETIME(6),
    duration_seconds BIGINT,
    status VARCHAR(64),
    device_id BIGINT,
    user_id BIGINT,
    PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS active_window_activity (
    id BIGINT NOT NULL AUTO_INCREMENT,
    window_title VARCHAR(1000),
    start_time DATETIME(6),
    offline_id VARCHAR(255),
    end_time DATETIME(6),
    duration_seconds BIGINT,
    status VARCHAR(64),
    device_id BIGINT,
    PRIMARY KEY (id),
    CONSTRAINT ux_active_window_offline_id UNIQUE (offline_id)
);

CREATE TABLE IF NOT EXISTS idle_activity (
    id BIGINT NOT NULL AUTO_INCREMENT,
    idle_start DATETIME(6),
    idle_end DATETIME(6),
    idle_seconds BIGINT,
    status VARCHAR(64),
    device_id BIGINT,
    user_id BIGINT,
    PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS device_session (
    id BIGINT NOT NULL AUTO_INCREMENT,
    startup_time DATETIME(6),
    shutdown_time DATETIME(6),
    session_duration_seconds BIGINT,
    status VARCHAR(64),
    device_id BIGINT,
    user_id BIGINT,
    PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS agent_devices (
    id BIGINT NOT NULL AUTO_INCREMENT,
    device_uuid VARCHAR(36) NOT NULL,
    legacy_device_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    machine_name VARCHAR(255),
    os_name VARCHAR(255),
    agent_version VARCHAR(64),
    last_ip_address VARCHAR(255),
    last_seen_at DATETIME(6),
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT ux_agent_devices_uuid UNIQUE (device_uuid)
);

CREATE INDEX ix_agent_devices_user ON agent_devices (user_id);
CREATE INDEX ix_agent_devices_seen ON agent_devices (last_seen_at);

CREATE TABLE IF NOT EXISTS agent_activities (
    record_uuid VARCHAR(36) NOT NULL,
    device_uuid VARCHAR(36) NOT NULL,
    user_id BIGINT NOT NULL,
    kind VARCHAR(32) NOT NULL,
    revision BIGINT NOT NULL,
    started_at DATETIME(6) NOT NULL,
    ended_at DATETIME(6),
    duration_millis BIGINT,
    state VARCHAR(32) NOT NULL,
    close_reason VARCHAR(64),
    process_id BIGINT,
    process_name VARCHAR(512),
    window_title VARCHAR(1000),
    legacy_record_id BIGINT,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (record_uuid)
);

CREATE INDEX ix_agent_activity_device_start
    ON agent_activities (device_uuid, started_at);
CREATE INDEX ix_agent_activity_user_start
    ON agent_activities (user_id, started_at);
CREATE INDEX ix_agent_activity_device_state
    ON agent_activities (device_uuid, state);
