ALTER TABLE refund DROP CHECK chk_refund_state;

ALTER TABLE refund
    ADD COLUMN user_id BIGINT UNSIGNED NULL AFTER order_id,
    ADD COLUMN trade_state_version BIGINT NOT NULL DEFAULT 0 AFTER qualification,
    ADD COLUMN source_version BIGINT NULL AFTER trade_state_version;

ALTER TABLE refund
    ADD CONSTRAINT chk_refund_state
        CHECK (state IN ('VALIDATING', 'FREEZE_CONFIRMED', 'CHANNEL_PENDING', 'CHANNEL_UNKNOWN',
                         'REVOKE_PENDING', 'CLOSE_RESTORE', 'COMPLETED', 'REJECTED', 'CLOSED', 'FAILED'));

ALTER TABLE refund
    ADD UNIQUE KEY uk_refund_intent (intent_id);

CREATE INDEX idx_refund_kind_state_updated ON refund (kind, state, updated_at);