package com.jobbootcamp.assignment.controller;

import com.jobbootcamp.assignment.application.AiReviewWorker;
import com.jobbootcamp.assignment.config.AssignmentAuthInterceptor;
import com.jobbootcamp.common.error.BusinessException;
import com.jobbootcamp.common.error.ErrorCode;
import com.jobbootcamp.common.web.ApiResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 本地验收用的显式 mock 触发入口；生产关闭 mock 时不注册。
 */
@RestController
@RequestMapping("/api/v1/dev/assignments")
@ConditionalOnProperty(name = "app.ai.mock-enabled", havingValue = "true", matchIfMissing = true)
public class MockReviewDevController {

    private final AiReviewWorker worker;

    public MockReviewDevController(AiReviewWorker worker) {
        this.worker = worker;
    }

    @PostMapping("/mock-review")
    public ApiResponse<Map<String, String>> run(
            @RequestAttribute(AssignmentAuthInterceptor.ROLE_ATTRIBUTE) String role) {
        if (!"ADMIN".equals(role)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "mock 点评触发仅面向管理员");
        }
        worker.poll();
        return ApiResponse.ok(Map.of("status", "triggered"));
    }
}