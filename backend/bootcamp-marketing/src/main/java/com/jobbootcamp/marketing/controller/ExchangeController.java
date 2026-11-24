package com.jobbootcamp.marketing.controller;

import com.jobbootcamp.common.web.ApiResponse;
import com.jobbootcamp.marketing.application.ExchangeService;
import com.jobbootcamp.marketing.config.MarketingAuthInterceptor;
import com.jobbootcamp.marketing.domain.dto.ExchangeProjectStudentView;
import com.jobbootcamp.marketing.domain.dto.ExchangeRecordView;
import com.jobbootcamp.marketing.domain.dto.ExchangeRequest;
import com.jobbootcamp.marketing.domain.dto.ExchangeResult;
import com.jobbootcamp.marketing.domain.dto.PageResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ExchangeController {

    private final ExchangeService service;

    public ExchangeController(ExchangeService service) {
        this.service = service;
    }

    @GetMapping("/api/v1/exchange-projects")
    public ApiResponse<PageResponse<ExchangeProjectStudentView>> available(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestAttribute(MarketingAuthInterceptor.USER_ID_ATTRIBUTE) Long userId,
            @RequestAttribute(MarketingAuthInterceptor.ROLE_ATTRIBUTE) String role) {
        return ApiResponse.ok(service.listAvailable(userId, role, page, pageSize));
    }

    @GetMapping("/api/v1/me/exchanges")
    public ApiResponse<PageResponse<ExchangeRecordView>> myExchanges(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestAttribute(MarketingAuthInterceptor.USER_ID_ATTRIBUTE) Long userId,
            @RequestAttribute(MarketingAuthInterceptor.ROLE_ATTRIBUTE) String role) {
        return ApiResponse.ok(service.myExchanges(userId, role, page, pageSize));
    }

    @PostMapping("/api/v1/exchange-projects/{id}/exchanges")
    public ApiResponse<ExchangeResult> exchange(
            @PathVariable("id") Long id,
            @RequestBody(required = false) ExchangeRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String requestKey,
            @RequestAttribute(MarketingAuthInterceptor.USER_ID_ATTRIBUTE) Long userId,
            @RequestAttribute(MarketingAuthInterceptor.ROLE_ATTRIBUTE) String role) {
        return ApiResponse.ok(service.exchange(userId, role, id,
                request == null ? null : request.expectedRevision(), requestKey));
    }
}