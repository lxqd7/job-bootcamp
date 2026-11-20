package com.jobbootcamp.learning.controller;

import com.jobbootcamp.common.error.BusinessException;
import com.jobbootcamp.common.error.ErrorCode;
import com.jobbootcamp.common.web.ApiResponse;
import com.jobbootcamp.learning.application.EntitlementService;
import com.jobbootcamp.learning.config.StudentAuthInterceptor;
import com.jobbootcamp.learning.domain.dto.MyCourseItem;
import com.jobbootcamp.learning.domain.dto.MyRouteItem;
import com.jobbootcamp.learning.domain.dto.PageResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me")
public class MyEntitlementController {

    private final EntitlementService entitlementService;

    public MyEntitlementController(EntitlementService entitlementService) {
        this.entitlementService = entitlementService;
    }

    @GetMapping("/courses")
    public ApiResponse<PageResponse<MyCourseItem>> courses(
            @RequestAttribute(StudentAuthInterceptor.USER_ID_ATTRIBUTE) Long userId,
            @RequestAttribute(StudentAuthInterceptor.ROLE_ATTRIBUTE) String role,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "pageSize", defaultValue = "20") int pageSize) {
        requireStudent(role);
        return ApiResponse.ok(entitlementService.myCourses(userId, page, pageSize));
    }

    @GetMapping("/routes")
    public ApiResponse<PageResponse<MyRouteItem>> routes(
            @RequestAttribute(StudentAuthInterceptor.USER_ID_ATTRIBUTE) Long userId,
            @RequestAttribute(StudentAuthInterceptor.ROLE_ATTRIBUTE) String role,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "pageSize", defaultValue = "20") int pageSize) {
        requireStudent(role);
        return ApiResponse.ok(entitlementService.myRoutes(userId, page, pageSize));
    }

    private void requireStudent(String role) {
        if (!"STUDENT".equals(role)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "学习权益仅面向学生账号");
        }
    }
}