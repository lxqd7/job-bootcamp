package com.jobbootcamp.trade.controller;

import com.jobbootcamp.common.error.BusinessException;
import com.jobbootcamp.common.error.ErrorCode;
import com.jobbootcamp.common.web.ApiResponse;
import com.jobbootcamp.trade.application.AdminOrderService;
import com.jobbootcamp.trade.config.StudentAuthInterceptor;
import com.jobbootcamp.trade.domain.dto.AdminOrderItem;
import com.jobbootcamp.trade.domain.dto.PageResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/orders")
public class AdminOrderController {

    private final AdminOrderService orderService;

    public AdminOrderController(AdminOrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping
    public ApiResponse<PageResponse<AdminOrderItem>> list(
            @RequestAttribute(StudentAuthInterceptor.ROLE_ATTRIBUTE) String role,
            @RequestParam(name = "state", required = false) String state,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "pageSize", defaultValue = "20") int pageSize) {
        if (!"ADMIN".equals(role)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "订单运营查询仅面向管理员");
        }
        return ApiResponse.ok(orderService.list(state, page, pageSize));
    }
}