package com.jobbootcamp.trade.payment;

import com.jobbootcamp.trade.domain.PaymentRequest;

/**
 * 支付渠道边界：业务代码只依赖本接口，真实渠道与 MOCK 实现不能改变订单状态判定。
 */
public interface PaymentPort {

    String channel();

    CreatedPayment create(PaymentRequest request);

    CloseResult close(PaymentRequest request);

    RefundResult refund(PaymentRequest request, String refundNo, long amountCent);

    boolean verify(String rawBody, String signature);

    record CreatedPayment(String payAction, String state) {
    }

    record CloseResult(String state, String code) {
    }

    record RefundResult(String state, String channelRefundNo, String code) {
    }
}