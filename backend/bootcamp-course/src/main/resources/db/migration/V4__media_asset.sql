CREATE TABLE media_asset
(
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    provider    VARCHAR(16)     NOT NULL,
    app_id      VARCHAR(64)     NULL,
    file_id     VARCHAR(128)    NULL,
    demo_path   VARCHAR(1000)   NULL,
    duration_ms BIGINT          NULL,
    status      VARCHAR(16)     NOT NULL DEFAULT 'PENDING',
    detached    BOOLEAN         NOT NULL DEFAULT FALSE,
    origin      VARCHAR(16)     NOT NULL,
    verified_at DATETIME(3)     NULL,
    old_id      VARCHAR(64)     NULL,
    created_at  DATETIME(3)     NOT NULL,
    updated_at  DATETIME(3)     NOT NULL,
    revision    BIGINT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_media_vod (provider, app_id, file_id),
    UNIQUE KEY uk_media_demo (provider, demo_path(700)),
    KEY idx_media_status_provider (status, provider, id),
    CONSTRAINT chk_media_provider CHECK (provider IN ('VOD', 'DEMO')),
    CONSTRAINT chk_media_status CHECK (status IN ('PENDING', 'AVAILABLE', 'MISSING', 'ERROR')),
    CONSTRAINT chk_media_origin CHECK (origin IN ('OLD_IMPORT', 'NEW', 'DEMO'))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE media_operation
(
    id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    operation_key CHAR(36)        NOT NULL,
    kind          VARCHAR(16)     NOT NULL,
    input_json    JSON            NOT NULL,
    input_hash    CHAR(64)        NOT NULL,
    result_json   JSON            NULL,
    state         VARCHAR(16)     NOT NULL,
    attempts      INT             NOT NULL DEFAULT 0,
    next_retry_at DATETIME(3)     NOT NULL,
    lease_token   CHAR(36)        NULL,
    lease_until   DATETIME(3)     NULL,
    created_at    DATETIME(3)     NOT NULL,
    updated_at    DATETIME(3)     NOT NULL,
    revision      BIGINT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_media_operation_key (operation_key),
    KEY idx_media_operation_state (state, next_retry_at, id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE media_import_plan
(
    batch_id   CHAR(36)    NOT NULL,
    old_id     VARCHAR(64) NOT NULL,
    app_id     VARCHAR(64) NOT NULL,
    file_id    VARCHAR(128) NOT NULL,
    input_hash CHAR(64)    NOT NULL,
    action     VARCHAR(16) NOT NULL,
    PRIMARY KEY (batch_id, old_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE import_mapping
(
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    batch_id    CHAR(36)        NOT NULL,
    entity_type VARCHAR(16)     NOT NULL,
    old_id      VARCHAR(64)     NOT NULL,
    new_id      BIGINT UNSIGNED NOT NULL,
    input_hash  CHAR(64)        NOT NULL,
    created_at  DATETIME(3)     NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_import_mapping (entity_type, old_id),
    KEY idx_import_mapping_batch (batch_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE play_session
(
    id              CHAR(36)        NOT NULL,
    user_id         BIGINT UNSIGNED NOT NULL,
    auth_session_id BIGINT UNSIGNED NOT NULL,
    course_id       BIGINT UNSIGNED NOT NULL,
    lesson_id       BIGINT UNSIGNED NOT NULL,
    media_id        BIGINT UNSIGNED NOT NULL,
    catalog_version BIGINT          NOT NULL,
    issued_at       DATETIME(3)     NOT NULL,
    expires_at      DATETIME(3)     NOT NULL,
    mode            VARCHAR(16)     NOT NULL,
    PRIMARY KEY (id),
    KEY idx_play_session_user (user_id, expires_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;