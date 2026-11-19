CREATE TABLE category_closure
(
    ancestor_id   BIGINT UNSIGNED NOT NULL,
    descendant_id BIGINT UNSIGNED NOT NULL,
    depth         INT             NOT NULL,
    PRIMARY KEY (ancestor_id, descendant_id),
    KEY idx_category_closure_descendant (descendant_id, depth)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

INSERT INTO category_closure (ancestor_id, descendant_id, depth)
SELECT id, id, 0
FROM category;

CREATE TABLE course
(
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    title           VARCHAR(200)    NOT NULL,
    description     MEDIUMTEXT      NOT NULL,
    category_id     BIGINT UNSIGNED NOT NULL,
    price_cent      BIGINT          NOT NULL,
    is_free         BOOLEAN         NOT NULL DEFAULT FALSE,
    status          VARCHAR(16)     NOT NULL DEFAULT 'DRAFT',
    cover_url       VARCHAR(1000)   NULL,
    catalog_version BIGINT          NOT NULL DEFAULT 0,
    created_at      DATETIME(3)     NOT NULL,
    updated_at      DATETIME(3)     NOT NULL,
    revision        BIGINT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_course_status_category (status, category_id, id),
    KEY idx_course_category (category_id),
    CONSTRAINT chk_course_status CHECK (status IN ('DRAFT', 'ONLINE', 'OFFLINE')),
    CONSTRAINT chk_course_price CHECK ((is_free = TRUE AND price_cent = 0) OR (is_free = FALSE AND price_cent >= 990))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE chapter
(
    id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    course_id  BIGINT UNSIGNED NOT NULL,
    title      VARCHAR(200)    NOT NULL,
    sort       INT             NOT NULL DEFAULT 0,
    deleted    BOOLEAN         NOT NULL DEFAULT FALSE,
    created_at DATETIME(3)     NOT NULL,
    updated_at DATETIME(3)     NOT NULL,
    revision   BIGINT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_chapter_course (course_id, deleted, sort, id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE lesson
(
    id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    course_id  BIGINT UNSIGNED NOT NULL,
    chapter_id BIGINT UNSIGNED NOT NULL,
    title      VARCHAR(200)    NOT NULL,
    sort       INT             NOT NULL DEFAULT 0,
    media_id   BIGINT UNSIGNED NULL,
    type       VARCHAR(16)     NOT NULL DEFAULT 'VIDEO',
    deleted    BOOLEAN         NOT NULL DEFAULT FALSE,
    created_at DATETIME(3)     NOT NULL,
    updated_at DATETIME(3)     NOT NULL,
    revision   BIGINT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_lesson_chapter (chapter_id, deleted, sort, id),
    KEY idx_lesson_course (course_id, deleted, id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;