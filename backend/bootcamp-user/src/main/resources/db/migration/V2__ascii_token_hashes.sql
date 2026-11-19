ALTER TABLE auth_session
    MODIFY refresh_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    MODIFY csrf_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL;

ALTER TABLE refresh_history
    MODIFY used_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL;