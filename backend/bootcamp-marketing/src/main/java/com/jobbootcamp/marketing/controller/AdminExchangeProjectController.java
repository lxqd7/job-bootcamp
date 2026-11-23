package com.jobbootcamp.marketing.controller;

import com.jobbootcamp.common.error.BusinessException;
import com.jobbootcamp.common.error.ErrorCode;
import com.jobbootcamp.common.web.ApiResponse;
import com.jobbootcamp.marketing.application.ExchangeProjectService;
import com.jobbootcamp.marketing.config.MarketingAuthInterceptor;
import com.jobbootcamp.marketing.domain.dto.ExchangeProjectRequest;
import com.jobbootcamp.marketing.domain.dto.ExchangeProjectStateRequest;
import com.jobbootcamp.marketing.domain.dto.ExchangeProjectView;
import com.jobbootcamp.marketing.domain.dto.PageResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/exchange-projects")
public class AdminExchangeProjectController {

    private final ExchangeProjectService service;

    public AdminExchangeProjectController(ExchangeProjectService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<PageResponse<ExchangeProjectView>> list(
            @RequestAttribute(MarketingAuthInterceptor.ROLE_ATTRIBUTE) String role,
            @RequestParam(required = false) String state,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        requireAdmin(role);
        return ApiResponse.ok(service.list(state, page, pageSize));
    }

    @GetMapping("/{id}")
    public ApiResponse<ExchangeProjectView> get(
            @PathVariable("id") Long id,
            @RequestAttribute(MarketingAuthInterceptor.ROLE_ATTRIBUTE) String role) {
        requireAdmin(role);
        return ApiResponse.ok(service.get(id));
    }

    @PostMapping
    public ApiResponse<ExchangeProjectView> create(
            @RequestBody ExchangeProjectRequest request,
            @RequestAttribute(MarketingAuthInterceptor.ROLE_ATTRIBUTE) String role) {
        requireAdmin(role);
        return ApiResponse.ok(service.create(request));
    }

    @PatchMapping("/{id}")
    public ApiResponse<ExchangeProjectView> update(
            @PathVariable("id") Long id,
            @RequestBody ExchangeProjectRequest request,
            @RequestAttribute(MarketingAuthInterceptor.ROLE_ATTRIBUTE) String role) {
        requireAdmin(role);
        return ApiResponse.ok(service.update(id, request));
    }

    @PostMapping("/{id}/{action}")
    public ApiResponse<ExchangeProjectView> state(
            @PathVariable("id") Long id,
            @PathVariable("action") String action,
            @RequestBody(required = false) ExchangeProjectStateRequest request,
            @RequestAttribute(MarketingAuthInterceptor.ROLE_ATTRIBUTE) String role) {
        requireAdmin(role);
        String target = switch (action) {
            case "online" -> "ONLINE";
            case "offline" -> "OFFLINE";
            default -> throw new BusinessException(ErrorCode.VALIDATION_FAILED, "未知兑换项目操作");
        };
        return ApiResponse.ok(service.changeState(id, target, request == null ? null : request.revision()));
    }

    private void requireAdmin(String role) {
        if (!"ADMIN".equals(role)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "兑换项目管理仅面向管理员");
        }
    }
}