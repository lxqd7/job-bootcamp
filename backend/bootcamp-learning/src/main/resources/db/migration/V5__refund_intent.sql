CREATE TABLE refund_intent
(
    id                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    request_id        CHAR(36)        NOT NULL,
    order_id          BIGINT UNSIGNED NOT NULL,
    user_id           BIGINT UNSIGNED NOT NULL,
    accepted_at       DATETIME(3)     NOT NULL,
    user_sequence     BIGINT          NOT NULL,
    paid_at           DATETIME(3)     NOT NULL,
    snapshot_hash     CHAR(64)       NOT NULL,
    product_type      VARCHAR(16)     NOT NULL,
    completed_count   INT             NOT NULL,
    total_count       INT             NOT NULL,
    source_id         BIGINT UNSIGNED NOT NULL,
    source_version    BIGINT          NOT NULL,
    state             VARCHAR(16)     NOT NULL,
    reason            VARCHAR(80)     NULL,
    active_order_id   BIGINT UNSIGNED NULL,
    created_at        DATETIME(3)     NOT NULL,
    updated_at        DATETIME(3)     NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_refund_intent_request (request_id),
    UNIQUE KEY uk_refund_intent_active_order (active_order_id),
    KEY idx_refund_intent_user_order (user_id, order_id, state),
    KEY idx_refund_intent_state (state, updated_at),
    CONSTRAINT chk_refund_intent_state CHECK (state IN ('ACCEPTED', 'PROCESSING', 'REJECTED', 'COMPLETED', 'CLOSED')),
    CONSTRAINT chk_refund_intent_progress CHECK (completed_count >= 0 AND total_count > 0 AND completed_count <= total_count)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE refund_intent_lesson
(
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    intent_id           BIGINT UNSIGNED NOT NULL,
    course_id           BIGINT UNSIGNED NOT NULL,
    lesson_id           BIGINT UNSIGNED NOT NULL,
    completed           BOOLEAN         NOT NULL,
    completion_sequence BIGINT          NULL,
    created_at          DATETIME(3)     NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_refund_intent_lesson (intent_id, lesson_id),
    KEY idx_refund_intent_lesson_course (course_id, lesson_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;