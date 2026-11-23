CREATE TABLE assignment
(
    id                 BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    kind               VARCHAR(24)     NOT NULL,
    course_id          BIGINT UNSIGNED NOT NULL,
    chapter_id         BIGINT UNSIGNED NULL,
    author_id          BIGINT UNSIGNED NOT NULL,
    status             VARCHAR(16)     NOT NULL,
    current_version_id BIGINT UNSIGNED NULL,
    draft_json         JSON            NOT NULL,
    revision           BIGINT          NOT NULL DEFAULT 0,
    formal_scope_key   VARCHAR(80)     NOT NULL,
    created_at         DATETIME(3)     NOT NULL,
    updated_at         DATETIME(3)     NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_assignment_scope (formal_scope_key),
    KEY idx_assignment_course_status (course_id, status, updated_at),
    CONSTRAINT chk_assignment_kind CHECK (kind IN ('CHAPTER_CHOICE', 'COURSE_CODE')),
    CONSTRAINT chk_assignment_status CHECK (status IN ('DRAFT', 'ONLINE', 'OFFLINE')),
    CONSTRAINT chk_assignment_scope CHECK (
        (kind = 'CHAPTER_CHOICE' AND chapter_id IS NOT NULL)
        OR (kind = 'COURSE_CODE' AND chapter_id IS NULL)
    )
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE assignment_version
(
    id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    assignment_id BIGINT UNSIGNED NOT NULL,
    version_no    INT             NOT NULL,
    public_body   JSON            NOT NULL,
    answer_body   JSON            NOT NULL,
    published_at  DATETIME(3)     NOT NULL,
    prompt_version VARCHAR(40)    NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_assignment_version (assignment_id, version_no)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE assignment_route
(
    id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    assignment_id BIGINT UNSIGNED NOT NULL,
    route_id      BIGINT UNSIGNED NOT NULL,
    epoch_id      BIGINT UNSIGNED NOT NULL,
    bound_at      DATETIME(3)     NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_assignment_route_epoch (assignment_id, route_id, epoch_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;