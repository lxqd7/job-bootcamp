CREATE TABLE excellent_reward
(
    note_id        BIGINT UNSIGNED NOT NULL,
    author_id      BIGINT UNSIGNED NOT NULL,
    teacher_id     BIGINT UNSIGNED NOT NULL,
    content_revision BIGINT        NOT NULL,
    awarded_at     DATETIME(3)     NOT NULL,
    reward_event_id CHAR(36)       NOT NULL,
    reward_version BIGINT          NOT NULL,
    revoked        BOOLEAN         NOT NULL DEFAULT FALSE,
    revoke_reason  VARCHAR(1000)   NULL,
    revoked_at     DATETIME(3)     NULL,
    PRIMARY KEY (note_id),
    KEY idx_excellent_author (author_id, awarded_at),
    KEY idx_excellent_teacher (teacher_id, awarded_at)
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
    payload           JSON            NOT NULL,
    status            VARCHAR(16)     NOT NULL DEFAULT 'NEW',
    attempt           INT             NOT NULL DEFAULT 0,
    next_retry_at     DATETIME(3)     NOT NULL,
    lease_token       CHAR(36)        NULL,
    lease_until       DATETIME(3)     NULL,
    error_code        VARCHAR(80)     NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_outbox_event_id (event_id),
    KEY idx_outbox_status_retry (status, next_retry_at, id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;