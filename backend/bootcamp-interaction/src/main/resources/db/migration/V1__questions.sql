CREATE TABLE question
(
    id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    course_id      BIGINT UNSIGNED NOT NULL,
    lesson_id      BIGINT UNSIGNED NULL,
    author_id      BIGINT UNSIGNED NOT NULL,
    title          VARCHAR(100)    NOT NULL,
    body           TEXT            NOT NULL,
    anonymous      BOOLEAN         NOT NULL DEFAULT FALSE,
    state          VARCHAR(16)     NOT NULL,
    last_answer_id BIGINT UNSIGNED NULL,
    revision       BIGINT          NOT NULL DEFAULT 0,
    created_at     DATETIME(3)     NOT NULL,
    updated_at     DATETIME(3)     NOT NULL,
    PRIMARY KEY (id),
    KEY idx_question_course_state (course_id, state, created_at, id),
    KEY idx_question_author (author_id, state, created_at, id),
    CONSTRAINT chk_question_state CHECK (state IN ('VISIBLE', 'DELETED', 'HIDDEN'))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE answer
(
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    question_id BIGINT UNSIGNED NOT NULL,
    author_id   BIGINT UNSIGNED NOT NULL,
    author_role VARCHAR(16)     NOT NULL,
    body        TEXT            NOT NULL,
    state       VARCHAR(16)     NOT NULL,
    like_count  INT             NOT NULL DEFAULT 0,
    revision    BIGINT          NOT NULL DEFAULT 0,
    created_at  DATETIME(3)     NOT NULL,
    updated_at  DATETIME(3)     NOT NULL,
    PRIMARY KEY (id),
    KEY idx_answer_question_state (question_id, state, created_at, id),
    CONSTRAINT chk_answer_state CHECK (state IN ('VISIBLE', 'DELETED', 'HIDDEN')),
    CONSTRAINT chk_answer_role CHECK (author_role IN ('STUDENT', 'TEACHER')),
    CONSTRAINT chk_answer_like_count CHECK (like_count >= 0)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE comment
(
    id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    answer_id  BIGINT UNSIGNED NOT NULL,
    author_id  BIGINT UNSIGNED NOT NULL,
    body       TEXT            NOT NULL,
    state      VARCHAR(16)     NOT NULL,
    like_count INT             NOT NULL DEFAULT 0,
    revision   BIGINT          NOT NULL DEFAULT 0,
    created_at DATETIME(3)     NOT NULL,
    updated_at DATETIME(3)     NOT NULL,
    PRIMARY KEY (id),
    KEY idx_comment_answer_state (answer_id, state, created_at, id),
    CONSTRAINT chk_comment_state CHECK (state IN ('VISIBLE', 'DELETED', 'HIDDEN')),
    CONSTRAINT chk_comment_like_count CHECK (like_count >= 0)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE moderation_action
(
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    target_type VARCHAR(24)     NOT NULL,
    target_id   BIGINT UNSIGNED NOT NULL,
    admin_id    BIGINT UNSIGNED NOT NULL,
    action      VARCHAR(24)     NOT NULL,
    reason      VARCHAR(1000)   NOT NULL,
    created_at  DATETIME(3)     NOT NULL,
    PRIMARY KEY (id),
    KEY idx_moderation_target (target_type, target_id, created_at),
    CONSTRAINT chk_moderation_action CHECK (action IN ('HIDE', 'TRACE_AUTHOR'))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;