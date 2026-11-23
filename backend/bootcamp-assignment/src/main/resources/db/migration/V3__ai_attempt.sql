CREATE TABLE ai_attempt
(
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    task_id     BIGINT UNSIGNED NOT NULL,
    call_index  INT             NOT NULL,
    provider    VARCHAR(32)     NOT NULL,
    state       VARCHAR(16)     NOT NULL,
    http_status INT             NULL,
    error_class VARCHAR(40)     NULL,
    started_at  DATETIME(3)     NOT NULL,
    finished_at DATETIME(3)     NULL,
    created_at  DATETIME(3)     NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_ai_attempt_task_call (task_id, call_index),
    KEY idx_ai_attempt_task_state (task_id, state),
    CONSTRAINT chk_ai_attempt_state CHECK (state IN ('PLANNED', 'SUCCESS', 'FAILED', 'TIMEOUT'))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;