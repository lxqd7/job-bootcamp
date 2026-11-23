CREATE TABLE note
(
    id                     BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    owner_id               BIGINT UNSIGNED NOT NULL,
    course_id              BIGINT UNSIGNED NOT NULL,
    lesson_id              BIGINT UNSIGNED NULL,
    position_ms            BIGINT UNSIGNED NULL,
    type                   VARCHAR(16)     NOT NULL,
    source_note_id         BIGINT UNSIGNED NULL,
    source_author_snapshot JSON            NULL,
    body                   MEDIUMTEXT      NOT NULL,
    visibility             VARCHAR(16)     NOT NULL,
    state                  VARCHAR(16)     NOT NULL,
    content_revision       BIGINT          NOT NULL DEFAULT 0,
    excellent              BOOLEAN         NOT NULL DEFAULT FALSE,
    created_at             DATETIME(3)     NOT NULL,
    updated_at             DATETIME(3)     NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_note_owner_source (owner_id, source_note_id),
    KEY idx_note_owner_state (owner_id, state, created_at, id),
    KEY idx_note_course_visibility (course_id, visibility, state, created_at, id),
    CONSTRAINT chk_note_type CHECK (type IN ('ORIGINAL', 'COLLECTED')),
    CONSTRAINT chk_note_visibility CHECK (visibility IN ('PRIVATE', 'COURSE_PUBLIC')),
    CONSTRAINT chk_note_state CHECK (state IN ('VISIBLE', 'DELETED', 'HIDDEN')),
    CONSTRAINT chk_note_collected_private CHECK (type = 'ORIGINAL' OR visibility = 'PRIVATE'),
    CONSTRAINT chk_note_source CHECK (
        (type = 'ORIGINAL' AND source_note_id IS NULL)
        OR (type = 'COLLECTED' AND source_note_id IS NOT NULL)
    )
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE note_version
(
    note_id          BIGINT UNSIGNED NOT NULL,
    content_revision BIGINT          NOT NULL,
    body             MEDIUMTEXT      NOT NULL,
    visibility       VARCHAR(16)     NOT NULL,
    created_at       DATETIME(3)     NOT NULL,
    PRIMARY KEY (note_id, content_revision)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;