CREATE TABLE excellent_revocation
(
    note_id             BIGINT UNSIGNED NOT NULL,
    original_event_id   CHAR(36)        NOT NULL,
    reason              VARCHAR(1000)   NOT NULL,
    occurred_at         DATETIME(3)     NOT NULL,
    state               VARCHAR(16)     NOT NULL DEFAULT 'PENDING',
    revoke_ledger_id    BIGINT UNSIGNED NULL,
    created_at          DATETIME(3)     NOT NULL,
    updated_at          DATETIME(3)     NOT NULL,
    PRIMARY KEY (note_id),
    KEY idx_excellent_revocation_state (state, created_at),
    CONSTRAINT chk_excellent_revocation_state CHECK (state IN ('PENDING', 'APPLIED'))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;