CREATE TABLE deduction_link
(
    id                 BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    source_order_id    BIGINT UNSIGNED NULL,
    dependent_order_id BIGINT UNSIGNED NOT NULL,
    course_id          BIGINT UNSIGNED NOT NULL,
    source_id          BIGINT UNSIGNED NOT NULL,
    source_grant_id    BIGINT UNSIGNED NOT NULL,
    state              VARCHAR(16)     NOT NULL,
    created_at         DATETIME(3)     NOT NULL,
    updated_at         DATETIME(3)     NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_deduction_dependent_course (dependent_order_id, course_id),
    KEY idx_deduction_source_state (source_order_id, state),
    CONSTRAINT chk_deduction_state CHECK (state IN ('RESERVED', 'COMMITTED', 'RELEASED'))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;