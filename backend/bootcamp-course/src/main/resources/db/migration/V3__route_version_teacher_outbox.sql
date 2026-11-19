CREATE TABLE course_version
(
    id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    course_id    BIGINT UNSIGNED NOT NULL,
    version_no   BIGINT          NOT NULL,
    published_at DATETIME(3)     NOT NULL,
    snapshot     JSON            NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_course_version (course_id, version_no)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE learning_route
(
    id                 BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    title              VARCHAR(200)    NOT NULL,
    description        MEDIUMTEXT      NOT NULL,
    price_cent         BIGINT          NOT NULL,
    status             VARCHAR(16)     NOT NULL DEFAULT 'DRAFT',
    current_version_no BIGINT          NOT NULL DEFAULT 0,
    created_at         DATETIME(3)     NOT NULL,
    updated_at         DATETIME(3)     NOT NULL,
    revision           BIGINT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_learning_route_status (status, id),
    CONSTRAINT chk_route_status CHECK (status IN ('DRAFT', 'ONLINE', 'OFFLINE')),
    CONSTRAINT chk_route_price CHECK (price_cent > 0)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE route_version
(
    id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    route_id     BIGINT UNSIGNED NOT NULL,
    version_no   BIGINT          NOT NULL,
    effective_at DATETIME(3)     NOT NULL,
    snapshot     JSON            NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_route_version (route_id, version_no),
    KEY idx_route_version_effective (route_id, effective_at, version_no)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE route_member_epoch
(
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    route_id        BIGINT UNSIGNED NOT NULL,
    course_id       BIGINT UNSIGNED NOT NULL,
    joined_version  BIGINT          NOT NULL,
    removed_version BIGINT          NULL,
    joined_at       DATETIME(3)     NOT NULL,
    removed_at      DATETIME(3)     NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_route_member_epoch (route_id, course_id, joined_version),
    KEY idx_route_epoch_course (route_id, course_id, joined_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE route_member_current
(
    route_id BIGINT UNSIGNED NOT NULL,
    course_id BIGINT UNSIGNED NOT NULL,
    epoch_id BIGINT UNSIGNED NOT NULL,
    sort     INT             NOT NULL DEFAULT 0,
    PRIMARY KEY (route_id, course_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE route_draft_member
(
    route_id  BIGINT UNSIGNED NOT NULL,
    course_id BIGINT UNSIGNED NOT NULL,
    sort      INT             NOT NULL DEFAULT 0,
    PRIMARY KEY (route_id, course_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE teacher_scope
(
    teacher_id     BIGINT UNSIGNED NOT NULL,
    scope_revision BIGINT          NOT NULL DEFAULT 0,
    PRIMARY KEY (teacher_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE teacher_course
(
    teacher_id BIGINT UNSIGNED NOT NULL,
    course_id  BIGINT UNSIGNED NOT NULL,
    active     BOOLEAN         NOT NULL DEFAULT TRUE,
    PRIMARY KEY (teacher_id, course_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE teacher_route
(
    teacher_id BIGINT UNSIGNED NOT NULL,
    route_id   BIGINT UNSIGNED NOT NULL,
    active     BOOLEAN         NOT NULL DEFAULT TRUE,
    PRIMARY KEY (teacher_id, route_id)
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