CREATE TABLE free_claim_ticket
(
    claim_id       CHAR(36)        NOT NULL,
    user_id        BIGINT UNSIGNED NOT NULL,
    course_id      BIGINT UNSIGNED NOT NULL,
    course_version BIGINT          NOT NULL,
    status         VARCHAR(16)     NOT NULL,
    source_id      BIGINT UNSIGNED NULL,
    accepted_at    DATETIME(3)     NOT NULL,
    created_at     DATETIME(3)     NOT NULL,
    updated_at     DATETIME(3)     NOT NULL,
    revision       BIGINT          NOT NULL DEFAULT 0,
    PRIMARY KEY (claim_id),
    KEY idx_claim_user_course (user_id, course_id, created_at),
    KEY idx_claim_status (status, updated_at),
    CONSTRAINT chk_claim_status CHECK (status IN ('PROCESSING', 'FULFILLED', 'FAILED'))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;