DROP TABLE IF EXISTS idle_activity_dedupe_map;
CREATE TABLE idle_activity_dedupe_map AS
SELECT i.id AS duplicate_id,
       (SELECT MIN(i2.id)
          FROM idle_activity i2
         WHERE i2.device_id = i.device_id
           AND i2.idle_start = i.idle_start) AS keep_id
  FROM idle_activity i
 WHERE i.device_id IS NOT NULL
   AND i.idle_start IS NOT NULL
   AND i.id <> (SELECT MIN(i3.id)
                  FROM idle_activity i3
                 WHERE i3.device_id = i.device_id
                   AND i3.idle_start = i.idle_start);

UPDATE agent_activities
   SET legacy_record_id = (
       SELECT MIN(m.keep_id)
         FROM idle_activity_dedupe_map m
        WHERE m.duplicate_id = agent_activities.legacy_record_id)
 WHERE kind = 'IDLE'
   AND legacy_record_id IN (SELECT duplicate_id FROM idle_activity_dedupe_map);

DELETE FROM idle_activity
 WHERE id IN (SELECT duplicate_id FROM idle_activity_dedupe_map);
DROP TABLE idle_activity_dedupe_map;

CREATE UNIQUE INDEX ux_idle_activity_device_start
    ON idle_activity (device_id, idle_start);
