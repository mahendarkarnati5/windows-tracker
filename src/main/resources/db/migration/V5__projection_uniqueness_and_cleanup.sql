-- One-time cleanup runs before the application accepts traffic. Runtime duplicate scans are
-- disabled by default in v2.2.6; unique keys and idempotent upserts prevent recurrence.

DROP TABLE IF EXISTS process_activity_dedupe_group;
CREATE TABLE process_activity_dedupe_group AS
SELECT device_id,
       pid,
       start_time,
       MIN(id) AS keep_id,
       MAX(process_name) AS merged_process_name,
       CASE
           WHEN MAX(CASE WHEN UPPER(status) = 'RUNNING' THEN 1 ELSE 0 END) = 1 THEN 'RUNNING'
           WHEN MAX(CASE WHEN UPPER(status) = 'CLOSED' THEN 1 ELSE 0 END) = 1 THEN 'CLOSED'
           WHEN MAX(CASE WHEN UPPER(status) = 'INTERRUPTED' THEN 1 ELSE 0 END) = 1 THEN 'INTERRUPTED'
           ELSE 'OFFLINE'
       END AS merged_status,
       CASE
           WHEN MAX(CASE WHEN UPPER(status) = 'RUNNING' THEN 1 ELSE 0 END) = 1 THEN NULL
           WHEN MAX(CASE WHEN UPPER(status) = 'CLOSED' THEN 1 ELSE 0 END) = 1
               THEN MAX(CASE WHEN UPPER(status) = 'CLOSED' THEN end_time END)
           WHEN MAX(CASE WHEN UPPER(status) = 'INTERRUPTED' THEN 1 ELSE 0 END) = 1
               THEN MAX(CASE WHEN UPPER(status) = 'INTERRUPTED' THEN end_time END)
           ELSE MAX(end_time)
       END AS merged_end_time,
       CASE
           WHEN MAX(CASE WHEN UPPER(status) = 'RUNNING' THEN 1 ELSE 0 END) = 1 THEN NULL
           WHEN MAX(CASE WHEN UPPER(status) = 'CLOSED' THEN 1 ELSE 0 END) = 1
               THEN MAX(CASE WHEN UPPER(status) = 'CLOSED' THEN duration_seconds END)
           WHEN MAX(CASE WHEN UPPER(status) = 'INTERRUPTED' THEN 1 ELSE 0 END) = 1
               THEN MAX(CASE WHEN UPPER(status) = 'INTERRUPTED' THEN duration_seconds END)
           ELSE MAX(duration_seconds)
       END AS merged_duration_seconds
  FROM process_activity
 WHERE device_id IS NOT NULL
   AND pid IS NOT NULL
   AND start_time IS NOT NULL
 GROUP BY device_id, pid, start_time
HAVING COUNT(id) > 1;

DROP TABLE IF EXISTS process_activity_dedupe_map;
CREATE TABLE process_activity_dedupe_map AS
SELECT p.id AS duplicate_id, g.keep_id
  FROM process_activity p
  JOIN process_activity_dedupe_group g
    ON g.device_id = p.device_id
   AND g.pid = p.pid
   AND g.start_time = p.start_time
 WHERE p.id <> g.keep_id;

UPDATE process_activity
   SET process_name = COALESCE(
           (SELECT MAX(g.merged_process_name)
              FROM process_activity_dedupe_group g
             WHERE g.keep_id = process_activity.id),
           process_name),
       status = (SELECT MAX(g.merged_status)
                   FROM process_activity_dedupe_group g
                  WHERE g.keep_id = process_activity.id),
       end_time = (SELECT MAX(g.merged_end_time)
                     FROM process_activity_dedupe_group g
                    WHERE g.keep_id = process_activity.id),
       duration_seconds = (SELECT MAX(g.merged_duration_seconds)
                             FROM process_activity_dedupe_group g
                            WHERE g.keep_id = process_activity.id)
 WHERE id IN (SELECT keep_id FROM process_activity_dedupe_group);

UPDATE agent_activities
   SET legacy_record_id = (
       SELECT MIN(m.keep_id)
         FROM process_activity_dedupe_map m
        WHERE m.duplicate_id = agent_activities.legacy_record_id)
 WHERE kind = 'PROCESS'
   AND legacy_record_id IN (SELECT duplicate_id FROM process_activity_dedupe_map);

DELETE FROM process_activity
 WHERE id IN (SELECT duplicate_id FROM process_activity_dedupe_map);

DROP TABLE process_activity_dedupe_map;
DROP TABLE process_activity_dedupe_group;

CREATE UNIQUE INDEX ux_process_activity_device_pid_start
    ON process_activity (device_id, pid, start_time);
