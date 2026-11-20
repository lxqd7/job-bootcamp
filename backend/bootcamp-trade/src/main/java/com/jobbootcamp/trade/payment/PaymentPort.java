package com.jobbootcamp.trade.payment;

import com.jobbootcamp.trade.domain.PaymentRequest;

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