package com.jobbootcamp.marketing.controller;

import com.jobbootcamp.common.error.BusinessException;
import com.jobbootcamp.common.error.ErrorCode;
import com.jobbootcamp.common.web.ApiResponse;
import com.jobbootcamp.marketing.application.PointsService;
import com.jobbootcamp.marketing.config.MarketingAuthInterceptor;
import com.jobbootcamp.marketing.domain.dto.SignResult;
import com.jobbootcamp.marketing.domain.dto.SignStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/sign-ins")
public class SignController {

    private final PointsService pointsService;

    public SignController(PointsService pointsService) {
        this.pointsService = pointsService;
    }

    @PostMapping
    public ApiResponse<SignResult> sign(
            @RequestAttribute(MarketingAuthInterceptor.USER_ID_ATTRIBUTE) Long userId,
            @RequestAttribute(MarketingAuthInterceptor.ROLE_ATTRIBUTE) String role) {
        requireStudent(role);
        return ApiResponse.ok(pointsService.signIn(userId));
    }

    @GetMapping
    public ApiResponse<SignStatus> status(
            @RequestAttribute(MarketingAuthInterceptor.USER_ID_ATTRIBUTE) Long userId,
            @RequestAttribute(MarketingAuthInterceptor.ROLE_ATTRIBUTE) String role,
            @RequestParam(name = "month", required = false) String month) {
        requireStudent(role);
        return ApiResponse.ok(pointsService.signStatus(userId, month));
    }

    private void requireStudent(String role) {
        if (!"STUDENT".equals(role)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "签到仅面向学生账号");
        }
    }
}