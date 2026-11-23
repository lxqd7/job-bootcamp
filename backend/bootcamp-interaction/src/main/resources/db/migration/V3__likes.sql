CREATE TABLE like_record
(
    user_id     BIGINT UNSIGNED NOT NULL,
    target_type VARCHAR(16)     NOT NULL,
    target_id   BIGINT UNSIGNED NOT NULL,
    active      BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at  DATETIME(3)     NOT NULL,
    updated_at  DATETIME(3)     NOT NULL,
    PRIMARY KEY (user_id, target_type, target_id),
    KEY idx_like_target (target_type, target_id, active),
    CONSTRAINT chk_like_target CHECK (target_type IN ('ANSWER', 'COMMENT'))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;