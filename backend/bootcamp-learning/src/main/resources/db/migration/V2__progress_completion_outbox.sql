CREATE TABLE lesson_progress
(
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id             BIGINT UNSIGNED NOT NULL,
    course_id           BIGINT UNSIGNED NOT NULL,
    lesson_id           BIGINT UNSIGNED NOT NULL,
    position_ms         BIGINT          NOT NULL DEFAULT 0,
    max_position_ms     BIGINT          NOT NULL DEFAULT 0,
    completed           BOOLEAN         NOT NULL DEFAULT FALSE,
    completed_at        DATETIME(3)     NULL,
    completion_sequence BIGINT          NULL,
    created_at          DATETIME(3)     NOT NULL,
    updated_at          DATETIME(3)     NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_progress_user_lesson (user_id, lesson_id),
    KEY idx_progress_user_course (user_id, course_id, completed),
    CONSTRAINT chk_progress_position CHECK (position_ms >= 0 AND max_position_ms >= 0)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE completion_event
(
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id     BIGINT UNSIGNED NOT NULL,
    event_sequence BIGINT       NOT NULL,
    course_id   BIGINT UNSIGNED NOT NULL,
    lesson_id   BIGINT UNSIGNED NOT NULL,
    occurred_at DATETIME(3)     NOT NULL,
    business_date DATE          NOT NULL,
    payload     JSON            NOT NULL,
    created_at  DATETIME(3)     NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_completion_user_sequence (user_id, event_sequence),
    UNIQUE KEY uk_completion_user_lesson (user_id, lesson_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE outbox_event
(
    id                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    event_id          CHAR(36)        NOT NULL,
    type              VARCHAR(80)     NOT NULL,
    aggregate_id      VARCHAR(64)     NOT NULL,
    aggregate_version BIGINT          NOT NULL,
    occurred_at       DATETIME(3)     NOT NULL,
    trace_id          VARCHAR(64)     NULL,
    payload           JSON            NOT NULL,
    status            VARCHAR(16)     NOT NULL DEFAULT 'PENDING',
    attempt           INT             NOT NULL DEFAULT 0,
    next_retry_at     DATETIME(3)     NOT NULL,
    lease_token       CHAR(36)        NULL,
    lease_until       DATETIME(3)     NULL,
    error_code        VARCHAR(80)     NULL,
    created_at        DATETIME(3)     NOT NULL,
    updated_at        DATETIME(3)     NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_learning_outbox_event_id (event_id),
    KEY idx_learning_outbox_status_retry (status, next_retry_at, id),
    CONSTRAINT chk_learning_outbox_status CHECK (status IN ('PENDING', 'SENT', 'NEEDS_ATTENTION'))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;