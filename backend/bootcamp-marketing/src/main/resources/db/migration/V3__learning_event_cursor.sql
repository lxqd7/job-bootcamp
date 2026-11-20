CREATE TABLE learning_event_cursor
(
    user_id       BIGINT UNSIGNED NOT NULL,
    last_sequence BIGINT          NOT NULL DEFAULT 0,
    updated_at    DATETIME(3)     NOT NULL,
    PRIMARY KEY (user_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;