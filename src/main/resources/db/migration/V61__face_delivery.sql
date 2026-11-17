ALTER TABLE DeliveryRequest ADD COLUMN authentication_method VARCHAR(32) NOT NULL DEFAULT 'QR_CODE';
ALTER TABLE StaffProfile
 ADD COLUMN face_avatar_version BINARY(16) NULL,
 ADD COLUMN face_status VARCHAR(24) NULL,
 ADD COLUMN face_template TEXT NULL,
 ADD COLUMN face_model_version VARCHAR(64) NULL,
 ADD COLUMN face_reviewed_by BINARY(16) NULL,
 ADD COLUMN face_reviewed_at DATETIME(6) NULL,
 ADD COLUMN face_row_version BIGINT NOT NULL DEFAULT 0;
UPDATE StaffProfile SET face_avatar_version=UUID_TO_BIN(UUID()),
 face_status=CASE WHEN url IS NULL OR url='' THEN 'MISSING' ELSE 'PENDING' END;
ALTER TABLE AuthVerification ADD COLUMN face_avatar_version BINARY(16) NULL;
ALTER TABLE AuthVerification DROP CHECK ck_auth_verification_method;
ALTER TABLE AuthVerification ADD CONSTRAINT ck_auth_verification_method
 CHECK (method IN ('QR_CODE','STAFF_CONFIRMATION','FACE_VERIFICATION'));
CREATE TABLE face_auth_session (
 session_id BINARY(16) PRIMARY KEY,
 job_id BINARY(16) NOT NULL,
 auth_job_step_id BINARY(16) NOT NULL UNIQUE,
 robot_id BINARY(16) NOT NULL,
 transport_session_id BINARY(16) NOT NULL,
 account_id BINARY(16) NOT NULL,
 avatar_version BINARY(16) NOT NULL,
 expires_at DATETIME(6) NOT NULL,
 used_at DATETIME(6) NULL,
 CONSTRAINT fk_face_job FOREIGN KEY(job_id) REFERENCES Job(job_id),
 CONSTRAINT fk_face_step FOREIGN KEY(auth_job_step_id) REFERENCES JobStep(job_step_id),
 CONSTRAINT fk_face_robot FOREIGN KEY(robot_id) REFERENCES Robot(robot_id),
 CONSTRAINT fk_face_account FOREIGN KEY(account_id) REFERENCES Account(account_id)
);
