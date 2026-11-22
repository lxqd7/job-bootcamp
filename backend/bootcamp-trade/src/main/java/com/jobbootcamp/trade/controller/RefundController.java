package com.jobbootcamp.trade.controller;

import com.jobbootcamp.common.error.BusinessException;
import com.jobbootcamp.common.error.ErrorCode;
import com.jobbootcamp.common.web.ApiResponse;
import com.jobbootcamp.trade.application.RefundService;
import com.jobbootcamp.trade.config.StudentAuthInterceptor;
import com.jobbootcamp.trade.domain.dto.RefundResponse;
import com.jobbootcamp.trade.domain.dto.RefundStatusResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/refunds")
public class RefundController {

    private final RefundService refundService;

    public RefundController(RefundService refundService) {
        this.refundService = refundService;
    }

    @GetMapping("/{id}")
    public ApiResponse<RefundResponse> get(
            @PathVariable("id") Long refundId,
            @RequestAttribute(StudentAuthInterceptor.USER_ID_ATTRIBUTE) Long userId,
            @RequestAttribute(StudentAuthInterceptor.ROLE_ATTRIBUTE) String role) {
        if (!"STUDENT".equals(role)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "退款单仅面向学生账号");
        }
        return ApiResponse.ok(refundService.get(userId, refundId));
    }

    @GetMapping("/by-order/{orderId}")
    public ApiResponse<RefundStatusResponse> getByOrder(
            @PathVariable("orderId") Long orderId,
            @RequestAttribute(StudentAuthInterceptor.USER_ID_ATTRIBUTE) Long userId,
            @RequestAttribute(StudentAuthInterceptor.ROLE_ATTRIBUTE) String role) {
        if (!"STUDENT".equals(role)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "退款单仅面向学生账号");
        }
        return ApiResponse.ok(refundService.getByOrder(userId, orderId));
    }
}