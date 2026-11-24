CREATE TABLE exchange_record
(
    id               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id          BIGINT UNSIGNED NOT NULL,
    project_id       BIGINT UNSIGNED NOT NULL,
    project_revision BIGINT          NOT NULL,
    request_key      VARCHAR(64)     NOT NULL,
    points_spent     INT             NOT NULL,
    user_coupon_id   BIGINT UNSIGNED NOT NULL,
    ledger_id        BIGINT UNSIGNED NOT NULL,
    balance_after    BIGINT          NOT NULL,
    created_at       DATETIME(3)     NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_exchange_record_user_request (user_id, request_key),
    UNIQUE KEY uk_exchange_record_coupon (user_coupon_id),
    KEY idx_exchange_record_project (project_id, created_at),
    KEY idx_exchange_record_ledger (ledger_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;