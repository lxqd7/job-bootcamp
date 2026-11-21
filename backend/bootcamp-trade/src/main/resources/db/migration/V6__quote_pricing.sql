ALTER TABLE price_quote ADD COLUMN coupon_plan_json JSON NULL;

ALTER TABLE price_quote DROP CHECK chk_quote_status;

ALTER TABLE price_quote
    ADD CONSTRAINT chk_quote_status
        CHECK (status IN ('READY', 'CALCULATING', 'CONSUMED', 'STALE', 'EXPIRED', 'FAILED'));