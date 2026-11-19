package com.jobbootcamp.marketing.controller;

import com.jobbootcamp.common.error.BusinessException;
import com.jobbootcamp.common.error.ErrorCode;
import com.jobbootcamp.common.web.ApiResponse;
import com.jobbootcamp.marketing.application.PointsService;
import com.jobbootcamp.marketing.config.MarketingAuthInterceptor;
import com.jobbootcamp.marketing.domain.dto.PageResponse;
import com.jobbootcamp.marketing.domain.dto.PointsLedgerView;
import com.jobbootcamp.marketing.domain.dto.PointsSummary;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me/points")
public class PointsController {

    private final PointsService pointsService;

    public PointsController(PointsService pointsService) {
        this.pointsService = pointsService;
    }

    @GetMapping
    public ApiResponse<PointsSummary> summary(
            @RequestAttribute(MarketingAuthInterceptor.USER_ID_ATTRIBUTE) Long userId,
            @RequestAttribute(MarketingAuthInterceptor.ROLE_ATTRIBUTE) String role) {
        requireStudent(role);
        return ApiResponse.ok(pointsService.summary(userId));
    }

    @GetMapping("/ledger")
    public ApiResponse<PageResponse<PointsLedgerView>> ledger(
            @RequestAttribute(MarketingAuthInterceptor.USER_ID_ATTRIBUTE) Long userId,
            @RequestAttribute(MarketingAuthInterceptor.ROLE_ATTRIBUTE) String role,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "pageSize", defaultValue = "20") int pageSize) {
        requireStudent(role);
        return ApiResponse.ok(pointsService.ledger(userId, page, pageSize));
    }

    private void requireStudent(String role) {
        if (!"STUDENT".equals(role)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "积分仅面向学生账号");
        }
    }
}