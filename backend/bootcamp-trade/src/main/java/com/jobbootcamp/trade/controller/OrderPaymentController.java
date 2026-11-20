package com.jobbootcamp.trade.controller;

import com.jobbootcamp.common.error.BusinessException;
import com.jobbootcamp.common.error.ErrorCode;
import com.jobbootcamp.common.web.ApiResponse;
import com.jobbootcamp.trade.application.OrderService;
import com.jobbootcamp.trade.application.PaymentService;
import com.jobbootcamp.trade.config.StudentAuthInterceptor;
import com.jobbootcamp.trade.domain.TradeOrder;
import com.jobbootcamp.trade.domain.dto.OrderResponse;
import com.jobbootcamp.trade.domain.dto.PaymentInitiateRequest;
import com.jobbootcamp.trade.domain.dto.PaymentInitiateResponse;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/orders")
public class OrderPaymentController {

    private final PaymentService paymentService;
    private final OrderService orderService;

    public OrderPaymentController(PaymentService paymentService, OrderService orderService) {
        this.paymentService = paymentService;
        this.orderService = orderService;
    }

    @PostMapping("/{id}/payment")
    public ApiResponse<PaymentInitiateResponse> payment(
            @PathVariable("id") Long orderId,
            @RequestAttribute(StudentAuthInterceptor.USER_ID_ATTRIBUTE) Long userId,
            @RequestAttribute(StudentAuthInterceptor.ROLE_ATTRIBUTE) String role,
            @RequestBody(required = false) PaymentInitiateRequest request) {
        requireStudent(role);
        return ApiResponse.ok(paymentService.initiate(userId, orderId, request));
    }

    @PostMapping("/{id}/cancel")
    public ApiResponse<OrderResponse> cancel(
            @PathVariable("id") Long orderId,
            @RequestAttribute(StudentAuthInterceptor.USER_ID_ATTRIBUTE) Long userId,
            @RequestAttribute(StudentAuthInterceptor.ROLE_ATTRIBUTE) String role) {
        requireStudent(role);
        TradeOrder order = paymentService.closeByUser(userId, orderId);
        return ApiResponse.ok(orderService.toResponse(order));
    }

    private void requireStudent(String role) {
        if (!"STUDENT".equals(role)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "订单支付仅面向学生账号");
        }
    }
}