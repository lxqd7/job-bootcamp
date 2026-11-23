ALTER TABLE ai_attempt
    ADD COLUMN model VARCHAR(128) NULL AFTER provider,
    ADD COLUMN response_hash CHAR(64) NULL AFTER error_class;