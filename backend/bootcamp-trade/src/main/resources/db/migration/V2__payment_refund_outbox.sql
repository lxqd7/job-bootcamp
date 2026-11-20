CREATE TABLE payment_request
(
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    order_id            BIGINT UNSIGNED NOT NULL,
    channel             VARCHAR(16)     NOT NULL,
    merchant_request_no VARCHAR(128)    NOT NULL,
    amount_cent         BIGINT          NOT NULL,
    state               VARCHAR(16)     NOT NULL,
    expires_at          DATETIME(3)     NOT NULL,
    created_at          DATETIME(3)     NOT NULL,
    updated_at          DATETIME(3)     NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_payment_request_merchant (channel, merchant_request_no),
    UNIQUE KEY uk_payment_request_order_channel (order_id, channel),
    KEY idx_payment_request_state_expiry (state, expires_at),
    CONSTRAINT chk_payment_request_amount CHECK (amount_cent > 0),
    CONSTRAINT chk_payment_request_state CHECK (state IN ('INIT', 'PENDING', 'UNKNOWN', 'CLOSED'))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE payment
(
    id                 BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    order_id           BIGINT UNSIGNED NOT NULL,
    payment_request_id BIGINT UNSIGNED NOT NULL,
    channel            VARCHAR(16)     NOT NULL,
    channel_trade_no   VARCHAR(128)    NOT NULL,
    amount_cent        BIGINT          NOT NULL,
    currency           CHAR(3)         NOT NULL,
    status             VARCHAR(16)     NOT NULL,
    paid_at            DATETIME(3)     NOT NULL,
    time_precision_ms  INT             NOT NULL,
    evidence_hash      CHAR(64)        NOT NULL,
    created_at         DATETIME(3)     NOT NULL,
    updated_at         DATETIME(3)     NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_payment_channel_trade (channel, channel_trade_no),
    KEY idx_payment_order_status (order_id, status),
    KEY idx_payment_request (payment_request_id),
    CONSTRAINT chk_payment_amount CHECK (amount_cent > 0),
    CONSTRAINT chk_payment_currency CHECK (currency = 'CNY'),
    CONSTRAINT chk_payment_status CHECK (status IN ('SUCCEEDED', 'CLOSED'))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE refund
(
    id                       BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    refund_no                VARCHAR(40)     NOT NULL,
    order_id                 BIGINT UNSIGNED NOT NULL,
    payment_id               BIGINT UNSIGNED NOT NULL,
    intent_id                BIGINT          NULL,
    kind                     VARCHAR(16)     NOT NULL,
    state                    VARCHAR(24)     NOT NULL,
    amount_cent              BIGINT          NOT NULL,
    accepted_at              DATETIME(3)     NOT NULL,
    progress_n               INT             NULL,
    progress_d               INT             NULL,
    system_return_payment_id BIGINT UNSIGNED NULL,
    channel_refund_no        VARCHAR(128)    NULL,
    qualification            JSON            NULL,
    active_order_id          BIGINT UNSIGNED NULL,
    created_at               DATETIME(3)     NOT NULL,
    updated_at               DATETIME(3)     NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_refund_no (refund_no),
    UNIQUE KEY uk_refund_system_return_payment (system_return_payment_id),
    UNIQUE KEY uk_refund_active_order (active_order_id),
    KEY idx_refund_order (order_id),
    KEY idx_refund_state_updated (state, updated_at),
    CONSTRAINT chk_refund_kind CHECK (kind IN ('USER', 'SYSTEM_RETURN')),
    CONSTRAINT chk_refund_state CHECK (state IN ('CHANNEL_PENDING', 'CHANNEL_UNKNOWN', 'COMPLETED', 'CLOSED', 'FAILED')),
    CONSTRAINT chk_refund_amount CHECK (amount_cent > 0)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE channel_receipt
(
    id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    channel      VARCHAR(16)     NOT NULL,
    receipt_id   VARCHAR(128)    NOT NULL,
    body_hash    CHAR(64)        NOT NULL,
    verified     BOOLEAN         NOT NULL,
    event_type   VARCHAR(64)     NOT NULL,
    payment_id   BIGINT UNSIGNED NULL,
    processed_at DATETIME(3)     NULL,
    created_at   DATETIME(3)     NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_channel_receipt (channel, receipt_id),
    KEY idx_channel_receipt_payment (payment_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE outbox_event
(
    id                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    event_id          CHAR(36)        NOT NULL,
    type              VARCHAR(80)     NOT NULL,
    aggregate_id      VARCHAR(64)     NOT NULL,
    aggregate_version BIGINT          NOT NULL,
    occurred_at       DATETIME(3)     NOT NULL,
    trace_id          VARCHAR(64)     NULL,
    payload           JSON            NOT NULL,
    status            VARCHAR(16)     NOT NULL DEFAULT 'PENDING',
    attempt           INT             NOT NULL DEFAULT 0,
    next_retry_at     DATETIME(3)     NOT NULL,
    lease_token       CHAR(36)        NULL,
    lease_until       DATETIME(3)     NULL,
    error_code        VARCHAR(80)     NULL,
    created_at        DATETIME(3)     NOT NULL,
    updated_at        DATETIME(3)     NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_trade_outbox_event_id (event_id),
    KEY idx_trade_outbox_status_retry (status, next_retry_at, id),
    CONSTRAINT chk_trade_outbox_status CHECK (status IN ('PENDING', 'SENT', 'NEEDS_ATTENTION'))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;