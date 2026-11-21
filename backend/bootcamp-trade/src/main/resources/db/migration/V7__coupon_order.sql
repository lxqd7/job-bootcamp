ALTER TABLE price_quote
    ADD COLUMN pricing_hash CHAR(64) NULL AFTER result_hash,
    ADD COLUMN coupon_mode VARCHAR(16) NOT NULL DEFAULT 'NONE' AFTER pricing_hash;

ALTER TABLE trade_order
    ADD COLUMN quote_id CHAR(36) NULL AFTER payment_sequence,
    ADD COLUMN pricing_hash CHAR(64) NULL AFTER quote_id,
    ADD COLUMN coupon_mode VARCHAR(16) NOT NULL DEFAULT 'NONE' AFTER pricing_hash,
    ADD COLUMN coupon_state VARCHAR(16) NOT NULL DEFAULT 'NONE' AFTER coupon_mode;

CREATE TABLE order_coupon
(
    id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    order_id       BIGINT UNSIGNED NOT NULL,
    user_coupon_id BIGINT UNSIGNED NOT NULL,
    apply_order    INT             NOT NULL,
    base_cent      BIGINT          NOT NULL,
    discount_cent  BIGINT          NOT NULL,
    allocation_json JSON           NOT NULL,
    created_at     DATETIME(3)     NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_order_coupon_order_coupon (order_id, user_coupon_id),
    KEY idx_order_coupon_coupon (user_coupon_id),
    CONSTRAINT chk_order_coupon_amount CHECK (base_cent >= 0 AND discount_cent >= 0)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;