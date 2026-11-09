ALTER TABLE `Shift` ADD COLUMN row_version BIGINT NOT NULL DEFAULT 0, ADD COLUMN checked_in_at DATETIME(6) NULL, ADD COLUMN checked_out_at DATETIME(6) NULL;
CREATE INDEX ix_shift_attendance ON `Shift` (status, ends_at, checked_in_at, checked_out_at);
ALTER TABLE DeliveryRequest ADD COLUMN receiver_shift_id BINARY(16) NULL,
 ADD CONSTRAINT fk_delivery_receiver_shift FOREIGN KEY (receiver_shift_id) REFERENCES `Shift` (shift_id);
