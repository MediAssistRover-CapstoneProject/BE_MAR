-- Read-only checks after importing 01_mar_to_aiven.sql into defaultdb.
SELECT DATABASE(), VERSION();
SHOW SESSION STATUS LIKE 'Ssl_cipher';
SELECT installed_rank, version, description, success FROM flyway_schema_history ORDER BY installed_rank;
SELECT BIN_TO_UUID(robot_id) AS robot_id, robot_code, status FROM Robot;
SELECT BIN_TO_UUID(map_id) AS map_id, map_code, map_name, status, asset_hash FROM Map;
SELECT COUNT(*) AS endpoints FROM Endpoint;
SELECT COUNT(*) AS zones FROM Zone;
SELECT COUNT(*) AS workflows FROM Workflow;
SELECT status, COUNT(*) FROM Job GROUP BY status;
SELECT COUNT(*) AS active_or_recovery_jobs FROM Job WHERE status IN ('QUEUED','ACCEPTED','RUNNING','RECOVERY_REQUIRED');
