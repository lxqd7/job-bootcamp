CREATE TABLE pricing_job
(
    quote_id     CHAR(36)        NOT NULL,
    user_id      BIGINT UNSIGNED NOT NULL,
    status       VARCHAR(16)     NOT NULL,
    request_json JSON            NOT NULL,
    result_json  JSON            NULL,
    error_code   VARCHAR(80)     NULL,
    error_message VARCHAR(500)   NULL,
    created_at   DATETIME(3)     NOT NULL,
    updated_at   DATETIME(3)     NOT NULL,
    PRIMARY KEY (quote_id),
    KEY idx_pricing_job_status (status, updated_at),
    CONSTRAINT chk_pricing_job_status CHECK (status IN ('CALCULATING', 'READY', 'FAILED'))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;