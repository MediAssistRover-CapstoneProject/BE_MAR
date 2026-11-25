-- One durable episode/return intent per robot; no synthetic values in Robot telemetry.
CREATE TABLE RobotBatteryPolicy (
 robot_id BINARY(16) NOT NULL PRIMARY KEY,
 episode_id BINARY(16) NULL,
 policy_state VARCHAR(32) NOT NULL DEFAULT 'NORMAL',
 low_notified BOOLEAN NOT NULL DEFAULT FALSE,
 critical_notified BOOLEAN NOT NULL DEFAULT FALSE,
 critical_latched BOOLEAN NOT NULL DEFAULT FALSE,
 return_requested BOOLEAN NOT NULL DEFAULT FALSE,
 last_sample_at DATETIME(6) NULL,
 critical_since DATETIME(6) NULL,
 rearm_since DATETIME(6) NULL,
 return_job_id BINARY(16) NULL,
 warning_job_id BINARY(16) NULL,
 return_source VARCHAR(16) NOT NULL DEFAULT 'AUTOMATIC',
 charger_endpoint_id BINARY(16) NULL,
 blocked_reason VARCHAR(1000) NULL,
 last_evaluated_at DATETIME(6) NULL,
 test_percent DECIMAL(5,2) NULL,
 test_expires_at DATETIME(6) NULL,
 test_actor_id BINARY(16) NULL,
 row_version BIGINT NOT NULL DEFAULT 0,
 CONSTRAINT FK_BatteryPolicy_robot FOREIGN KEY (robot_id) REFERENCES Robot(robot_id)
);
