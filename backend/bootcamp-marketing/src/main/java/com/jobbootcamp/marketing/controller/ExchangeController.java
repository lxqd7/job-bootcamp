package com.jobbootcamp.marketing.controller;

import com.jobbootcamp.common.web.ApiResponse;
import com.jobbootcamp.marketing.application.ExchangeService;
import com.jobbootcamp.marketing.config.MarketingAuthInterceptor;
import com.jobbootcamp.marketing.domain.dto.ExchangeRequest;
import com.jobbootcamp.marketing.domain.dto.ExchangeResult;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ExchangeController {

    private final ExchangeService service;

    public ExchangeController(ExchangeService service) {
        this.service = service;
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