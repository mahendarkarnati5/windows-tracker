DROP TABLE IF EXISTS device_session_dedupe_map;
CREATE TABLE device_session_dedupe_map AS
SELECT s.id AS duplicate_id,
       (SELECT MIN(s2.id)
          FROM device_session s2
         WHERE s2.device_id = s.device_id
           AND s2.startup_time = s.startup_time) AS keep_id
  FROM device_session s
 WHERE s.device_id IS NOT NULL
   AND s.startup_time IS NOT NULL
   AND s.id <> (SELECT MIN(s3.id)
                  FROM device_session s3
                 WHERE s3.device_id = s.device_id
                   AND s3.startup_time = s.startup_time);

UPDATE agent_activities
   SET legacy_record_id = (
       SELECT MIN(m.keep_id)
         FROM device_session_dedupe_map m
        WHERE m.duplicate_id = agent_activities.legacy_record_id)
 WHERE kind = 'DEVICE_SESSION'
   AND legacy_record_id IN (SELECT duplicate_id FROM device_session_dedupe_map);

DELETE FROM device_session
 WHERE id IN (SELECT duplicate_id FROM device_session_dedupe_map);
DROP TABLE device_session_dedupe_map;

CREATE UNIQUE INDEX ux_device_session_device_start
    ON device_session (device_id, startup_time);
