ALTER TABLE play_session
    ADD COLUMN duration_ms BIGINT NULL AFTER media_id;