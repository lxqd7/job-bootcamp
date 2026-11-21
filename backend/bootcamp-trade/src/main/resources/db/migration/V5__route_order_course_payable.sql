-- 路线订单中已抵扣/免费课程的单课实付可以为 0；整单最低实付仍由 trade_order 保证 >=100。
ALTER TABLE order_course DROP CHECK chk_order_course_payable;

ALTER TABLE order_course
    ADD CONSTRAINT chk_order_course_payable CHECK (payable_cent >= 0);