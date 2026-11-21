ALTER TABLE pricing_job
    ADD COLUMN result_hash CHAR(64) NULL AFTER result_json,
    ADD COLUMN attempts INT NOT NULL DEFAULT 0 AFTER result_hash,
    ADD COLUMN lease_token CHAR(36) NULL AFTER attempts,
    ADD COLUMN lease_until DATETIME(3) NULL AFTER lease_token;

CREATE INDEX idx_pricing_job_lease ON pricing_job (status, lease_until);