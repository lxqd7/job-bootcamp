CREATE TABLE coupon_lock
(
    id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    order_id       BIGINT UNSIGNED NOT NULL,
    user_id        BIGINT UNSIGNED NOT NULL,
    user_coupon_id BIGINT UNSIGNED NOT NULL,
    quote_id       CHAR(36)        NOT NULL,
    pricing_hash   CHAR(64)        NOT NULL,
    plan_json      JSON            NOT NULL,
    state          VARCHAR(16)     NOT NULL,
    created_at     DATETIME(3)     NOT NULL,
    updated_at     DATETIME(3)     NOT NULL,
    revision       BIGINT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_coupon_lock_order_coupon (order_id, user_coupon_id),
    KEY idx_coupon_lock_state_updated (state, updated_at),
    KEY idx_coupon_lock_coupon (user_coupon_id),
    CONSTRAINT chk_coupon_lock_state CHECK (state IN ('LOCKED', 'USED', 'RELEASED'))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;