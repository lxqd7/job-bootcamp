CREATE TABLE user_guard
(
    user_id                  BIGINT UNSIGNED NOT NULL,
    sequence                 BIGINT          NOT NULL DEFAULT 0,
    completion_event_sequence BIGINT          NOT NULL DEFAULT 0,
    created_at               DATETIME(3)     NOT NULL,
    updated_at               DATETIME(3)     NOT NULL,
    PRIMARY KEY (user_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE entitlement_source
(
    id                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id           BIGINT UNSIGNED NOT NULL,
    type              VARCHAR(16)     NOT NULL,
    source_key        VARCHAR(80)     NOT NULL,
    order_id          BIGINT UNSIGNED NULL,
    route_id          BIGINT UNSIGNED NULL,
    acquired_at       DATETIME(3)     NOT NULL,
    paid_at           DATETIME(3)     NULL,
    state             VARCHAR(16)     NOT NULL,
    lifecycle_version BIGINT          NOT NULL DEFAULT 1,
    created_at        DATETIME(3)     NOT NULL,
    updated_at        DATETIME(3)     NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_source_user_type_key (user_id, type, source_key),
    KEY idx_source_user_state (user_id, state, acquired_at, id),
    CONSTRAINT chk_source_type CHECK (type IN ('FREE', 'COURSE_ORDER', 'ROUTE_ORDER')),
    CONSTRAINT chk_source_state CHECK (state IN ('ACTIVE', 'FROZEN', 'REVOKED'))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE course_grant
(
    id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id       BIGINT UNSIGNED NOT NULL,
    course_id     BIGINT UNSIGNED NOT NULL,
    source_id     BIGINT UNSIGNED NOT NULL,
    granted_at    DATETIME(3)     NOT NULL,
    grant_sequence BIGINT         NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_grant_source_course (source_id, course_id),
    KEY idx_grant_user_course (user_id, course_id, source_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE route_entitlement
(
    id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id        BIGINT UNSIGNED NOT NULL,
    route_id       BIGINT UNSIGNED NOT NULL,
    source_id      BIGINT UNSIGNED NOT NULL,
    future_courses BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at     DATETIME(3)     NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_route_source (source_id, route_id),
    KEY idx_route_user (user_id, route_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE free_claim_receipt
(
    claim_id   CHAR(36)        NOT NULL,
    user_id    BIGINT UNSIGNED NOT NULL,
    course_id  BIGINT UNSIGNED NOT NULL,
    source_id  BIGINT UNSIGNED NOT NULL,
    created_at DATETIME(3)     NOT NULL,
    PRIMARY KEY (claim_id),
    KEY idx_receipt_user_course (user_id, course_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;