package com.jobbootcamp.trade.application;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jobbootcamp.common.error.BusinessException;
import com.jobbootcamp.common.error.ErrorCode;
import com.jobbootcamp.trade.domain.PaymentRequest;
import com.jobbootcamp.trade.domain.TradeOrder;
import com.jobbootcamp.trade.domain.dto.OrderResponse;
import com.jobbootcamp.trade.infrastructure.PaymentRequestMapper;
import com.jobbootcamp.trade.infrastructure.TradeOrderMapper;
import com.jobbootcamp.trade.payment.MockPaymentAdapter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
@ConditionalOnProperty(name = "app.payment.mode", havingValue = "mock", matchIfMissing = true)
public class MockPaymentActionService {

    private final PaymentRequestMapper requestMapper;
    private final TradeOrderMapper orderMapper;
    private final MockPaymentAdapter mockPaymentAdapter;
    private final PaymentCallbackService callbackService;
    private final OrderService orderService;

    public MockPaymentActionService(PaymentRequestMapper requestMapper,
                                    TradeOrderMapper orderMapper,
                                    MockPaymentAdapter mockPaymentAdapter,
                                    PaymentCallbackService callbackService,
                                    OrderService orderService) {
        this.requestMapper = requestMapper;
        this.orderMapper = orderMapper;
        this.mockPaymentAdapter = mockPaymentAdapter;
        this.callbackService = callbackService;
        this.orderService = orderService;
    }

    public OrderResponse pay(Long userId, Long orderId) {
        TradeOrder order = orderMapper.selectById(orderId);
        if (order == null || !order.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "订单不存在");
        }
        if ("PAID".equals(order.getState())) {
            return orderService.toResponse(order);
        }
        PaymentRequest request = requestMapper.selectOne(Wrappers.<PaymentRequest>lambdaQuery()
                .eq(PaymentRequest::getOrderId, orderId)
                .eq(PaymentRequest::getChannel, "MOCK")
                .orderByDesc(PaymentRequest::getId)
                .last("LIMIT 1"));
        if (request == null) {
            throw new BusinessException(ErrorCode.CONFLICT, "请先创建模拟支付单");
        }
        String paidAt = Instant.now().toString();
        String payload = "{\"merchantRequestNo\":\"" + request.getMerchantRequestNo()
                + "\",\"channelTradeNo\":\"MOCK-PAY-" + request.getId()
                + "\",\"amountCent\":" + request.getAmountCent()
                + ",\"currency\":\"CNY\",\"paidAt\":\"" + paidAt + "\"}";
        callbackService.handle("MOCK", payload, mockPaymentAdapter.sign(payload));
        TradeOrder updated = orderMapper.selectById(orderId);
        return orderService.toResponse(updated);
    }
}