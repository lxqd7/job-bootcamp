CREATE TABLE grant_route_member
(
    source_id   BIGINT UNSIGNED NOT NULL,
    epoch_id    BIGINT UNSIGNED NOT NULL,
    course_id   BIGINT UNSIGNED NOT NULL,
    qualified_at DATETIME(3)    NOT NULL,
    created_at  DATETIME(3)     NOT NULL,
    PRIMARY KEY (source_id, epoch_id),
    KEY idx_grant_route_member_course (course_id, source_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE route_epoch_projection
(
    epoch_id    BIGINT UNSIGNED NOT NULL,
    route_id    BIGINT UNSIGNED NOT NULL,
    course_id   BIGINT UNSIGNED NOT NULL,
    joined_at   DATETIME(3)     NOT NULL,
    removed_at  DATETIME(3)     NULL,
    revision_no BIGINT          NOT NULL,
    is_free     BOOLEAN         NOT NULL DEFAULT FALSE,
    created_at  DATETIME(3)     NOT NULL,
    updated_at  DATETIME(3)     NOT NULL,
    PRIMARY KEY (epoch_id),
    KEY idx_route_epoch_projection_route (route_id, joined_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;