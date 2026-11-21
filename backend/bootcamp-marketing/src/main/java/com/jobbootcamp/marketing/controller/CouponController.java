package com.jobbootcamp.marketing.controller;

import com.jobbootcamp.common.error.BusinessException;
import com.jobbootcamp.common.error.ErrorCode;
import com.jobbootcamp.common.web.ApiResponse;
import com.jobbootcamp.marketing.application.CouponService;
import com.jobbootcamp.marketing.config.MarketingAuthInterceptor;
import com.jobbootcamp.marketing.domain.dto.ActivityView;
import com.jobbootcamp.marketing.domain.dto.PageResponse;
import com.jobbootcamp.marketing.domain.dto.UserCouponView;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CouponController {

    private final CouponService couponService;

    public CouponController(CouponService couponService) {
        this.couponService = couponService;
    }

    @GetMapping("/api/v1/activities")
    public ApiResponse<PageResponse<ActivityView>> activities(
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "pageSize", defaultValue = "20") int pageSize) {
        return ApiResponse.ok(couponService.listActivities(false, page, pageSize));
    }

    @GetMapping("/api/v1/activities/{id}")
    public ApiResponse<ActivityView> activity(@PathVariable("id") Long id) {
        return ApiResponse.ok(couponService.getActivity(id, false));
    }

    @PostMapping("/api/v1/activity-items/{id}/claims")
    public ApiResponse<UserCouponView> claim(
            @PathVariable("id") Long itemId,
            @RequestAttribute(MarketingAuthInterceptor.USER_ID_ATTRIBUTE) Long userId,
            @RequestAttribute(MarketingAuthInterceptor.ROLE_ATTRIBUTE) String role,
            @RequestHeader(name = "Idempotency-Key", required = false) String requestKey) {
        requireStudent(role);
        return ApiResponse.ok(couponService.claim(userId, requestKey, itemId));
    }

    @GetMapping("/api/v1/me/coupons")
    public ApiResponse<PageResponse<UserCouponView>> myCoupons(
            @RequestAttribute(MarketingAuthInterceptor.USER_ID_ATTRIBUTE) Long userId,
            @RequestAttribute(MarketingAuthInterceptor.ROLE_ATTRIBUTE) String role,
            @RequestParam(name = "displayStatus", required = false) String displayStatus,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "pageSize", defaultValue = "20") int pageSize) {
        requireStudent(role);
        return ApiResponse.ok(couponService.myCoupons(userId, displayStatus, page, pageSize));
    }

    private void requireStudent(String role) {
        if (!"STUDENT".equals(role)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "优惠券仅面向学生账号");
        }
    }
}