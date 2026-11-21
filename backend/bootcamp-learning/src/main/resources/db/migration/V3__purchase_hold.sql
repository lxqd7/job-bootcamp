CREATE TABLE purchase_hold
(
    order_id        BIGINT UNSIGNED NOT NULL,
    user_id         BIGINT UNSIGNED NOT NULL,
    course_id       BIGINT UNSIGNED NOT NULL,
    source_id       BIGINT UNSIGNED NULL,
    source_grant_id BIGINT UNSIGNED NULL,
    state           VARCHAR(16)     NOT NULL,
    prepared_at     DATETIME(3)     NOT NULL,
    created_at      DATETIME(3)     NOT NULL,
    updated_at      DATETIME(3)     NOT NULL,
    PRIMARY KEY (order_id, course_id),
    KEY idx_purchase_hold_source_state (source_id, state),
    CONSTRAINT chk_purchase_hold_state CHECK (state IN ('PREPARED', 'COMMITTED', 'RELEASED'))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;