CREATE TABLE coupon_rule
(
    id                 BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    rule_key           VARCHAR(64)     NOT NULL,
    version_no         INT             NOT NULL,
    type               VARCHAR(24)     NOT NULL,
    scope              VARCHAR(16)     NOT NULL,
    target_id          BIGINT UNSIGNED NULL,
    threshold_cent     BIGINT          NOT NULL DEFAULT 0,
    amount_cent        BIGINT          NULL,
    pay_rate_bps       INT             NULL,
    max_discount_cent  BIGINT          NULL,
    validity_type      VARCHAR(16)     NOT NULL,
    valid_from         DATETIME(3)     NULL,
    valid_until        DATETIME(3)     NULL,
    valid_days         INT             NULL,
    issued             BOOLEAN         NOT NULL DEFAULT FALSE,
    created_at         DATETIME(3)     NOT NULL,
    updated_at         DATETIME(3)     NOT NULL,
    revision           BIGINT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_coupon_rule_key_version (rule_key, version_no),
    KEY idx_coupon_rule_scope (scope, target_id),
    CONSTRAINT chk_coupon_rule_type CHECK (type IN ('NO_THRESHOLD', 'FULL_REDUCTION', 'DISCOUNT')),
    CONSTRAINT chk_coupon_rule_scope CHECK (scope IN ('ALL', 'COURSE', 'CATEGORY', 'ROUTE')),
    CONSTRAINT chk_coupon_rule_validity CHECK (validity_type IN ('FIXED', 'DAYS'))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE activity
(
    id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    name       VARCHAR(200)    NOT NULL,
    state      VARCHAR(16)     NOT NULL,
    open_at    DATETIME(3)     NOT NULL,
    close_at   DATETIME(3)     NOT NULL,
    created_at DATETIME(3)     NOT NULL,
    updated_at DATETIME(3)     NOT NULL,
    revision   BIGINT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_activity_state_open (state, open_at, close_at),
    CONSTRAINT chk_activity_state CHECK (state IN ('DRAFT', 'ACTIVE', 'PAUSED', 'ENDED'))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE activity_item
(
    id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    activity_id    BIGINT UNSIGNED NOT NULL,
    rule_id        BIGINT UNSIGNED NOT NULL,
    stock_total    INT             NOT NULL,
    issued_count   INT             NOT NULL DEFAULT 0,
    per_user_limit INT             NOT NULL,
    enabled        BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at     DATETIME(3)     NOT NULL,
    updated_at     DATETIME(3)     NOT NULL,
    revision       BIGINT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_activity_item_rule (activity_id, rule_id),
    KEY idx_activity_item_activity (activity_id, enabled),
    CONSTRAINT chk_activity_item_stock CHECK (stock_total >= 0 AND issued_count >= 0 AND issued_count <= stock_total),
    CONSTRAINT chk_activity_item_limit CHECK (per_user_limit > 0)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE acquisition_counter
(
    user_id       BIGINT UNSIGNED NOT NULL,
    source_type   VARCHAR(16)     NOT NULL,
    source_item_id BIGINT UNSIGNED NOT NULL,
    total_count   INT             NOT NULL DEFAULT 0,
    updated_at    DATETIME(3)     NOT NULL,
    PRIMARY KEY (user_id, source_type, source_item_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE acquisition
(
    id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id        BIGINT UNSIGNED NOT NULL,
    source_type    VARCHAR(16)     NOT NULL,
    source_item_id BIGINT UNSIGNED NOT NULL,
    user_coupon_id BIGINT UNSIGNED NOT NULL,
    request_key    VARCHAR(64)     NOT NULL,
    created_at     DATETIME(3)     NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_acquisition_request (user_id, source_type, source_item_id, request_key),
    KEY idx_acquisition_coupon (user_coupon_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE user_coupon
(
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id         BIGINT UNSIGNED NOT NULL,
    rule_id         BIGINT UNSIGNED NOT NULL,
    source_type     VARCHAR(16)     NOT NULL,
    source_group_id BIGINT UNSIGNED NOT NULL,
    source_item_id  BIGINT UNSIGNED NOT NULL,
    valid_from      DATETIME(3)     NOT NULL,
    valid_until     DATETIME(3)     NOT NULL,
    status          VARCHAR(16)     NOT NULL,
    locked_order_id BIGINT UNSIGNED NULL,
    used_order_id   BIGINT UNSIGNED NULL,
    created_at      DATETIME(3)     NOT NULL,
    updated_at      DATETIME(3)     NOT NULL,
    revision        BIGINT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_user_coupon_user_status (user_id, status, valid_until),
    KEY idx_user_coupon_locked_order (locked_order_id),
    CONSTRAINT chk_user_coupon_status CHECK (status IN ('AVAILABLE', 'LOCKED', 'USED'))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;