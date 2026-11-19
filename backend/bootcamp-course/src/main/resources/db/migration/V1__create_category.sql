CREATE TABLE category
(
    id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    parent_id  BIGINT UNSIGNED NOT NULL DEFAULT 0,
    name       VARCHAR(200)    NOT NULL,
    sort       INT             NOT NULL DEFAULT 0,
    enabled    BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at DATETIME(3)     NOT NULL,
    updated_at DATETIME(3)     NOT NULL,
    revision   BIGINT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_category_parent_sort (parent_id, sort, id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;