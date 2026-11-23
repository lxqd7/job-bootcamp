CREATE TABLE exchange_project
(
    id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    name           VARCHAR(100)    NOT NULL,
    rule_id        BIGINT UNSIGNED NOT NULL,
    points_price   INT             NOT NULL,
    stock_total    INT             NOT NULL,
    stock_used     INT             NOT NULL DEFAULT 0,
    per_user_limit INT             NOT NULL,
    state          VARCHAR(16)     NOT NULL,
    open_at        DATETIME(3)     NOT NULL,
    close_at       DATETIME(3)     NOT NULL,
    revision       BIGINT          NOT NULL DEFAULT 0,
    created_at     DATETIME(3)     NOT NULL,
    updated_at     DATETIME(3)     NOT NULL,
    PRIMARY KEY (id),
    KEY idx_exchange_project_state (state, open_at, close_at),
    KEY idx_exchange_project_rule (rule_id),
    CONSTRAINT chk_exchange_project_state CHECK (state IN ('ONLINE', 'OFFLINE')),
    CONSTRAINT chk_exchange_project_points CHECK (points_price > 0),
    CONSTRAINT chk_exchange_project_stock CHECK (stock_total >= 0 AND stock_used >= 0 AND stock_total >= stock_used),
    CONSTRAINT chk_exchange_project_limit CHECK (per_user_limit > 0),
    CONSTRAINT chk_exchange_project_window CHECK (open_at < close_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;