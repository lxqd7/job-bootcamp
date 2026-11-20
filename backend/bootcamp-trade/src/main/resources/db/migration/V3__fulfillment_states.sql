-- S06-a 的临时值 NOT_STARTED 统一为设计中的 PENDING；发权状态与订单状态独立。
ALTER TABLE trade_order DROP CHECK chk_order_fulfillment;

UPDATE trade_order
SET fulfillment_state = 'PENDING'
WHERE fulfillment_state = 'NOT_STARTED';

ALTER TABLE trade_order
    ADD CONSTRAINT chk_order_fulfillment
        CHECK (fulfillment_state IN ('PENDING', 'RUNNING', 'DONE', 'ATTENTION'));