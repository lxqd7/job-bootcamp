package com.jobbootcamp.trade.controller;

import com.jobbootcamp.common.error.BusinessException;
import com.jobbootcamp.common.error.ErrorCode;
import com.jobbootcamp.common.web.ApiResponse;
import com.jobbootcamp.trade.application.AdminRefundService;
import com.jobbootcamp.trade.application.RefundService;
import com.jobbootcamp.trade.application.UserRefundService;
import com.jobbootcamp.trade.config.StudentAuthInterceptor;
import com.jobbootcamp.trade.domain.Refund;
import com.jobbootcamp.trade.domain.dto.AdminRefundItem;
import com.jobbootcamp.trade.domain.dto.PageResponse;
import com.jobbootcamp.trade.domain.dto.RefundResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/refunds")
public class AdminRefundController {

    private final UserRefundService userRefundService;
    private final RefundService refundService;
    private final AdminRefundService adminRefundService;

    public AdminRefundController(UserRefundService userRefundService,
                                 RefundService refundService,
                                 AdminRefundService adminRefundService) {
        this.userRefundService = userRefundService;
        this.refundService = refundService;
        this.adminRefundService = adminRefundService;
    }

    @GetMapping
    public ApiResponse<PageResponse<AdminRefundItem>> list(
            @RequestParam(required = false) String state,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestAttribute(StudentAuthInterceptor.ROLE_ATTRIBUTE) String role) {
        if (!"ADMIN".equals(role)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "退款对账仅面向管理员");
        }
        return ApiResponse.ok(adminRefundService.list(state, page, pageSize));
    }

    @PostMapping("/{id}/reconcile")
    public ApiResponse<RefundResponse> reconcile(
            @PathVariable("id") Long refundId,
            @RequestAttribute(StudentAuthInterceptor.ROLE_ATTRIBUTE) String role) {
        if (!"ADMIN".equals(role)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "退款对账仅面向管理员");
        }
        Refund refund = userRefundService.process(refundId);
        if (refund == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "退款单不存在");
        }
        return ApiResponse.ok(refundService.toResponse(refund));
    }
}