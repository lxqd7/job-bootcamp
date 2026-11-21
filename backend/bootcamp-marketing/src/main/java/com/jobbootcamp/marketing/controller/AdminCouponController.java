package com.jobbootcamp.marketing.controller;

import com.jobbootcamp.common.error.BusinessException;
import com.jobbootcamp.common.error.ErrorCode;
import com.jobbootcamp.common.web.ApiResponse;
import com.jobbootcamp.marketing.application.CouponService;
import com.jobbootcamp.marketing.config.MarketingAuthInterceptor;
import com.jobbootcamp.marketing.domain.dto.ActivityItemRequest;
import com.jobbootcamp.marketing.domain.dto.ActivityRequest;
import com.jobbootcamp.marketing.domain.dto.ActivityView;
import com.jobbootcamp.marketing.domain.dto.CouponRuleRequest;
import com.jobbootcamp.marketing.domain.dto.CouponRuleView;
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
public class AdminCouponController {

    private final CouponService couponService;

    public AdminCouponController(CouponService couponService) {
        this.couponService = couponService;
    }

    @GetMapping("/api/v1/admin/coupon-rules")
    public ApiResponse<PageResponse<CouponRuleView>> rules(
            @RequestAttribute(MarketingAuthInterceptor.ROLE_ATTRIBUTE) String role,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "pageSize", defaultValue = "20") int pageSize) {
        requireAdmin(role);
        return ApiResponse.ok(couponService.listRules(page, pageSize));
    }

    @PostMapping("/api/v1/admin/coupon-rules")
    public ApiResponse<CouponRuleView> createRule(
            @RequestAttribute(MarketingAuthInterceptor.ROLE_ATTRIBUTE) String role,
            @RequestBody CouponRuleRequest request) {
        requireAdmin(role);
        return ApiResponse.ok(couponService.createRule(request));
    }

    @PatchMapping("/api/v1/admin/coupon-rules/{id}")
    public ApiResponse<CouponRuleView> updateRule(
            @PathVariable("id") Long id,
            @RequestAttribute(MarketingAuthInterceptor.ROLE_ATTRIBUTE) String role,
            @RequestBody CouponRuleRequest request) {
        requireAdmin(role);
        return ApiResponse.ok(couponService.updateRule(id, request));
    }

    @GetMapping("/api/v1/admin/activities")
    public ApiResponse<PageResponse<ActivityView>> activities(
            @RequestAttribute(MarketingAuthInterceptor.ROLE_ATTRIBUTE) String role,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "pageSize", defaultValue = "20") int pageSize) {
        requireAdmin(role);
        return ApiResponse.ok(couponService.listActivities(true, page, pageSize));
    }

    @GetMapping("/api/v1/admin/activities/{id}")
    public ApiResponse<ActivityView> activity(
            @PathVariable("id") Long id,
            @RequestAttribute(MarketingAuthInterceptor.ROLE_ATTRIBUTE) String role) {
        requireAdmin(role);
        return ApiResponse.ok(couponService.getActivity(id, true));
    }

    @PostMapping("/api/v1/admin/activities")
    public ApiResponse<ActivityView> createActivity(
            @RequestAttribute(MarketingAuthInterceptor.ROLE_ATTRIBUTE) String role,
            @RequestBody ActivityRequest request) {
        requireAdmin(role);
        return ApiResponse.ok(couponService.createActivity(request));
    }

    @PatchMapping("/api/v1/admin/activities/{id}")
    public ApiResponse<ActivityView> updateActivity(
            @PathVariable("id") Long id,
            @RequestAttribute(MarketingAuthInterceptor.ROLE_ATTRIBUTE) String role,
            @RequestBody ActivityRequest request) {
        requireAdmin(role);
        return ApiResponse.ok(couponService.updateActivity(id, request));
    }

    @PostMapping("/api/v1/admin/activities/{id}/items")
    public ApiResponse<ActivityView> addItem(
            @PathVariable("id") Long id,
            @RequestAttribute(MarketingAuthInterceptor.ROLE_ATTRIBUTE) String role,
            @RequestBody ActivityItemRequest request) {
        requireAdmin(role);
        return ApiResponse.ok(couponService.addItem(id, request));
    }

    @PatchMapping("/api/v1/admin/activity-items/{id}")
    public ApiResponse<ActivityView> updateItem(
            @PathVariable("id") Long id,
            @RequestAttribute(MarketingAuthInterceptor.ROLE_ATTRIBUTE) String role,
            @RequestBody ActivityItemRequest request) {
        requireAdmin(role);
        return ApiResponse.ok(couponService.updateItem(id, request));
    }

    @PostMapping("/api/v1/admin/activities/{id}/{action}")
    public ApiResponse<ActivityView> state(
            @PathVariable("id") Long id,
            @PathVariable("action") String action,
            @RequestAttribute(MarketingAuthInterceptor.ROLE_ATTRIBUTE) String role,
            @RequestBody ActivityRequest request) {
        requireAdmin(role);
        String target = switch (action) {
            case "activate" -> "ACTIVE";
            case "pause" -> "PAUSED";
            case "end" -> "ENDED";
            default -> throw new BusinessException(ErrorCode.VALIDATION_FAILED, "未知活动操作");
        };
        return ApiResponse.ok(couponService.changeState(id, target, request.revision()));
    }

    private void requireAdmin(String role) {
        if (!"ADMIN".equals(role)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "优惠活动配置仅面向管理员");
        }
    }
}