package com.jobbootcamp.trade.controller;

import com.jobbootcamp.common.error.BusinessException;
import com.jobbootcamp.common.error.ErrorCode;
import com.jobbootcamp.common.web.ApiResponse;
import com.jobbootcamp.trade.application.MockPaymentActionService;
import com.jobbootcamp.trade.config.StudentAuthInterceptor;
import com.jobbootcamp.trade.domain.dto.OrderResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/orders")
@ConditionalOnProperty(name = "app.payment.mode", havingValue = "mock", matchIfMissing = true)
public class MockPaymentController {

    private final MockPaymentActionService actionService;

    public MockPaymentController(MockPaymentActionService actionService) {
        this.actionService = actionService;
    }

    @PostMapping("/{id}/payment/mock-pay")
    public ApiResponse<OrderResponse> pay(
            @PathVariable("id") Long orderId,
            @RequestAttribute(StudentAuthInterceptor.USER_ID_ATTRIBUTE) Long userId,
            @RequestAttribute(StudentAuthInterceptor.ROLE_ATTRIBUTE) String role) {
        if (!"STUDENT".equals(role)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "模拟支付仅面向学生账号");
        }
        return ApiResponse.ok(actionService.pay(userId, orderId));
    }
}