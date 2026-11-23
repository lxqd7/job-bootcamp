CREATE TABLE attempt
(
    id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id        BIGINT UNSIGNED NOT NULL,
    assignment_id  BIGINT UNSIGNED NOT NULL,
    version_id     BIGINT UNSIGNED NOT NULL,
    started_at     DATETIME(3)     NOT NULL,
    status         VARCHAR(16)     NOT NULL,
    draft_json     JSON            NULL,
    draft_revision BIGINT          NOT NULL DEFAULT 0,
    created_at     DATETIME(3)     NOT NULL,
    updated_at     DATETIME(3)     NOT NULL,
    PRIMARY KEY (id),
    KEY idx_attempt_user_assignment (user_id, assignment_id, started_at, id),
    CONSTRAINT chk_attempt_status CHECK (status IN ('OPEN', 'SUBMITTED'))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE submission
(
    id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id       BIGINT UNSIGNED NOT NULL,
    assignment_id BIGINT UNSIGNED NOT NULL,
    version_id    BIGINT UNSIGNED NOT NULL,
    attempt_id    BIGINT UNSIGNED NOT NULL,
    answers_json  JSON            NOT NULL,
    score         INT             NULL,
    max_score     INT             NULL,
    review_state  VARCHAR(16)     NOT NULL,
    request_key   VARCHAR(64)     NOT NULL,
    request_hash  CHAR(64)       NOT NULL,
    submitted_at  DATETIME(3)     NOT NULL,
    created_at    DATETIME(3)     NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_submission_attempt (attempt_id),
    UNIQUE KEY uk_submission_request (user_id, attempt_id, request_key),
    KEY idx_submission_user_assignment (user_id, assignment_id, version_id, submitted_at, id),
    CONSTRAINT chk_submission_review_state CHECK (review_state IN ('GRADED', 'PENDING_REVIEW', 'FAILED'))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE ai_user_slot
(
    user_id        BIGINT UNSIGNED NOT NULL,
    active_task_id BIGINT UNSIGNED NULL,
    updated_at     DATETIME(3)     NOT NULL,
    PRIMARY KEY (user_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE ai_task
(
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    submission_id   BIGINT UNSIGNED NOT NULL,
    user_id         BIGINT UNSIGNED NOT NULL,
    assignment_id   BIGINT UNSIGNED NOT NULL,
    state           VARCHAR(16)     NOT NULL,
    round_no        INT             NOT NULL DEFAULT 0,
    call_count      INT             NOT NULL DEFAULT 0,
    repair_used     BOOLEAN         NOT NULL DEFAULT FALSE,
    next_retry_at   DATETIME(3)     NULL,
    deadline_at     DATETIME(3)     NOT NULL,
    lease_token     CHAR(36)        NULL,
    lease_until     DATETIME(3)     NULL,
    safe_error_code VARCHAR(80)     NULL,
    prompt_version  VARCHAR(40)     NOT NULL,
    created_at      DATETIME(3)     NOT NULL,
    updated_at      DATETIME(3)     NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_ai_task_submission (submission_id),
    KEY idx_ai_task_state_retry (state, next_retry_at),
    KEY idx_ai_task_state_lease (state, lease_until),
    CONSTRAINT chk_ai_task_state CHECK (state IN ('QUEUED', 'RUNNING', 'RETRY_WAIT', 'SUCCEEDED', 'FAILED'))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE ai_feedback
(
    task_id     BIGINT UNSIGNED NOT NULL,
    verdict     VARCHAR(16)     NOT NULL,
    summary     VARCHAR(1000)   NOT NULL,
    issues      JSON            NOT NULL,
    suggestions JSON            NOT NULL,
    limitations JSON            NOT NULL,
    is_mock     BOOLEAN         NOT NULL,
    created_at  DATETIME(3)     NOT NULL,
    PRIMARY KEY (task_id),
    CONSTRAINT chk_ai_feedback_verdict CHECK (verdict IN ('SATISFIED', 'PARTIAL', 'NEEDS_CHANGE'))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;