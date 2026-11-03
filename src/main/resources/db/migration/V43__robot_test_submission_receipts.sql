CREATE TABLE RobotTestSubmission (
    request_key VARCHAR(64) NOT NULL,
    actor_account_id BINARY(16) NOT NULL,
    request_fingerprint VARCHAR(64) NOT NULL,
    job_id BINARY(16) NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (request_key),
    KEY ix_robot_test_submission_created (created_at)
);
