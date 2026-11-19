CREATE TABLE points_account
(
    user_id    BIGINT UNSIGNED NOT NULL,
    available  BIGINT          NOT NULL DEFAULT 0,
    debt       BIGINT          NOT NULL DEFAULT 0,
    revision   BIGINT          NOT NULL DEFAULT 0,
    created_at DATETIME(3)     NOT NULL,
    updated_at DATETIME(3)     NOT NULL,
    PRIMARY KEY (user_id),
    CONSTRAINT chk_points_account_available CHECK (available >= 0),
    CONSTRAINT chk_points_account_debt CHECK (debt >= 0)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE points_ledger
(
    id                 BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id            BIGINT UNSIGNED NOT NULL,
    type               VARCHAR(16)     NOT NULL,
    business_key       VARCHAR(160)    NOT NULL,
    reward_points      BIGINT          NOT NULL,
    balance_delta      BIGINT          NOT NULL,
    debt_delta         BIGINT          NOT NULL,
    rank_delta         BIGINT          NOT NULL,
    balance_after      BIGINT          NOT NULL,
    debt_after         BIGINT          NOT NULL,
    business_date      DATE            NOT NULL,
    business_month     CHAR(7)         NOT NULL,
    occurred_at        DATETIME(3)     NOT NULL,
    original_ledger_id BIGINT UNSIGNED NULL,
    result_code        VARCHAR(40)     NULL,
    created_at         DATETIME(3)     NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_points_ledger_business (type, business_key),
    KEY idx_points_ledger_user (user_id, id),
    KEY idx_points_ledger_month_user (business_month, user_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE points_day_quota
(
    user_id        BIGINT UNSIGNED NOT NULL,
    business_date  DATE            NOT NULL,
    awarded_points INT             NOT NULL DEFAULT 0,
    revision       BIGINT          NOT NULL DEFAULT 0,
    updated_at     DATETIME(3)     NOT NULL,
    PRIMARY KEY (user_id, business_date),
    CONSTRAINT chk_points_day_quota_range CHECK (awarded_points >= 0 AND awarded_points <= 50)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE points_month
(
    month    CHAR(7)         NOT NULL,
    user_id  BIGINT UNSIGNED NOT NULL,
    score    BIGINT          NOT NULL DEFAULT 0,
    revision BIGINT          NOT NULL DEFAULT 0,
    PRIMARY KEY (month, user_id),
    KEY idx_points_month_rank (month, score DESC, user_id ASC),
    CONSTRAINT chk_points_month_score CHECK (score >= 0)
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
    UNIQUE KEY uk_marketing_outbox_event_id (event_id),
    KEY idx_marketing_outbox_status_retry (status, next_retry_at, id),
    CONSTRAINT chk_marketing_outbox_status CHECK (status IN ('PENDING', 'SENT', 'NEEDS_ATTENTION'))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE inbox_event
(
    consumer      VARCHAR(80)  NOT NULL,
    event_id      CHAR(36)     NOT NULL,
    business_key  VARCHAR(160) NOT NULL,
    payload_hash  CHAR(64)     NOT NULL,
    state         VARCHAR(16)  NOT NULL,
    payload       JSON         NOT NULL,
    next_retry_at DATETIME(3)  NULL,
    created_at    DATETIME(3)  NOT NULL,
    updated_at    DATETIME(3)  NOT NULL,
    PRIMARY KEY (consumer, event_id),
    KEY idx_marketing_inbox_business (consumer, business_key),
    CONSTRAINT chk_marketing_inbox_state CHECK (state IN ('PROCESSING', 'PROCESSED', 'FAILED'))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;