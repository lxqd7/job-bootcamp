CREATE TABLE price_quote
(
    id                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    quote_id          CHAR(36)        NOT NULL,
    user_id           BIGINT UNSIGNED NOT NULL,
    product_type      VARCHAR(16)     NOT NULL,
    product_id        BIGINT UNSIGNED NOT NULL,
    product_version   BIGINT          NOT NULL,
    product_title     VARCHAR(255)    NOT NULL,
    unit_price_cent   BIGINT          NOT NULL,
    total_cent        BIGINT          NOT NULL,
    credit_cent       BIGINT          NOT NULL DEFAULT 0,
    discount_cent     BIGINT          NOT NULL DEFAULT 0,
    payable_cent      BIGINT          NOT NULL,
    input_hash        CHAR(64)        NOT NULL,
    result_hash       CHAR(64)        NOT NULL,
    status            VARCHAR(16)     NOT NULL,
    input_json        JSON            NOT NULL,
    result_json       JSON            NOT NULL,
    expires_at        DATETIME(3)     NOT NULL,
    created_at        DATETIME(3)     NOT NULL,
    updated_at        DATETIME(3)     NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_quote_id (quote_id),
    KEY idx_quote_user_created (user_id, created_at),
    KEY idx_quote_status_expiry (status, expires_at),
    CONSTRAINT chk_quote_amount CHECK (total_cent = credit_cent + discount_cent + payable_cent),
    CONSTRAINT chk_quote_payable CHECK (payable_cent >= 100),
    CONSTRAINT chk_quote_status CHECK (status IN ('READY', 'CONSUMED', 'STALE', 'EXPIRED'))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE trade_order
(
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    order_no            VARCHAR(40)     NOT NULL,
    user_id             BIGINT UNSIGNED NOT NULL,
    product_type        VARCHAR(16)     NOT NULL,
    product_id          BIGINT UNSIGNED NOT NULL,
    state               VARCHAR(16)     NOT NULL,
    fulfillment_state   VARCHAR(16)     NOT NULL,
    payment_sequence    BIGINT          NULL,
    total_cent          BIGINT          NOT NULL,
    credit_cent         BIGINT          NOT NULL DEFAULT 0,
    discount_cent       BIGINT          NOT NULL DEFAULT 0,
    payable_cent        BIGINT          NOT NULL,
    created_at          DATETIME(3)     NOT NULL,
    expires_at          DATETIME(3)     NOT NULL,
    cancel_requested_at DATETIME(3)     NULL,
    paid_at             DATETIME(3)     NULL,
    snapshot_hash       CHAR(64)        NOT NULL,
    snapshot            JSON            NOT NULL,
    request_key         VARCHAR(64)     NOT NULL,
    request_hash        CHAR(64)        NOT NULL,
    updated_at          DATETIME(3)     NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_order_no (order_no),
    UNIQUE KEY uk_order_request (user_id, request_key),
    KEY idx_order_user_created (user_id, created_at, id),
    KEY idx_order_state_expiry (state, expires_at),
    CONSTRAINT chk_order_amount CHECK (total_cent = credit_cent + discount_cent + payable_cent),
    CONSTRAINT chk_order_payable CHECK (payable_cent >= 100),
    CONSTRAINT chk_order_state CHECK (state IN ('CREATING', 'WAIT_PAY', 'PAID', 'CLOSING', 'CLOSED', 'CREATE_FAILED', 'RECONCILING')),
    CONSTRAINT chk_order_fulfillment CHECK (fulfillment_state IN ('NOT_STARTED', 'PROCESSING', 'FULFILLED', 'FAILED'))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE order_course
(
    id                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    order_id          BIGINT UNSIGNED NOT NULL,
    course_id         BIGINT UNSIGNED NOT NULL,
    course_version    BIGINT          NOT NULL,
    epoch_id          BIGINT          NULL,
    unit_price_cent   BIGINT          NOT NULL,
    allocated_cent    BIGINT          NOT NULL,
    credit_cent       BIGINT          NOT NULL DEFAULT 0,
    coupon_cent       BIGINT          NOT NULL DEFAULT 0,
    payable_cent      BIGINT          NOT NULL,
    source_id         BIGINT          NULL,
    source_grant_id   BIGINT          NULL,
    is_free           BOOLEAN         NOT NULL DEFAULT FALSE,
    is_new_paid       BOOLEAN         NOT NULL DEFAULT TRUE,
    category_ids      JSON            NOT NULL,
    created_at        DATETIME(3)     NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_order_course (order_id, course_id),
    CONSTRAINT chk_order_course_amount CHECK (allocated_cent = credit_cent + coupon_cent + payable_cent),
    CONSTRAINT chk_order_course_payable CHECK (payable_cent >= 100)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE order_lesson
(
    order_id    BIGINT UNSIGNED NOT NULL,
    course_id   BIGINT UNSIGNED NOT NULL,
    lesson_id   BIGINT UNSIGNED NOT NULL,
    duration_ms BIGINT          NOT NULL DEFAULT 0,
    created_at  DATETIME(3)     NOT NULL,
    PRIMARY KEY (order_id, lesson_id),
    KEY idx_order_lesson_course (order_id, course_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE pending_course
(
    user_id    BIGINT UNSIGNED NOT NULL,
    course_id  BIGINT UNSIGNED NOT NULL,
    order_id   BIGINT UNSIGNED NOT NULL,
    created_at DATETIME(3)     NOT NULL,
    PRIMARY KEY (user_id, course_id),
    KEY idx_pending_order (order_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE user_trade_guard
(
    user_id          BIGINT UNSIGNED NOT NULL,
    confirm_sequence BIGINT          NOT NULL DEFAULT 0,
    created_at       DATETIME(3)     NOT NULL,
    updated_at       DATETIME(3)     NOT NULL,
    PRIMARY KEY (user_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE api_idempotency
(
    id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    actor_id      BIGINT UNSIGNED NOT NULL,
    operation     VARCHAR(80)     NOT NULL,
    request_key   VARCHAR(128)    NOT NULL,
    request_hash  CHAR(64)        NOT NULL,
    resource_id   VARCHAR(64)     NULL,
    status        VARCHAR(16)     NOT NULL,
    response_json JSON            NULL,
    expires_at    DATETIME(3)     NOT NULL,
    created_at    DATETIME(3)     NOT NULL,
    updated_at    DATETIME(3)     NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_idempotency_actor_operation_key (actor_id, operation, request_key),
    KEY idx_idempotency_status_expiry (status, expires_at),
    CONSTRAINT chk_idempotency_status CHECK (status IN ('IN_PROGRESS', 'COMPLETED', 'FAILED'))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;