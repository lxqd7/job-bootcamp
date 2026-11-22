package com.jobbootcamp.learning.controller;

import com.jobbootcamp.common.error.BusinessException;
import com.jobbootcamp.common.error.ErrorCode;
import com.jobbootcamp.common.web.ApiResponse;
import com.jobbootcamp.learning.application.RefundIntentService;
import com.jobbootcamp.learning.config.StudentAuthInterceptor;
import com.jobbootcamp.learning.domain.dto.RefundApplicationRequest;
import com.jobbootcamp.learning.domain.dto.RefundIntentView;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class RefundApplicationController {

    private final RefundIntentService refundIntentService;

    public RefundApplicationController(RefundIntentService refundIntentService) {
        this.refundIntentService = refundIntentService;
    }

    @PostMapping("/orders/{orderId}/refund-applications")
    public ResponseEntity<ApiResponse<RefundIntentView>> apply(
            @PathVariable("orderId") Long orderId,
            @RequestAttribute(StudentAuthInterceptor.USER_ID_ATTRIBUTE) Long userId,
            @RequestAttribute(StudentAuthInterceptor.ROLE_ATTRIBUTE) String role,
            @RequestBody(required = false) RefundApplicationRequest request) {
        if (!"STUDENT".equals(role)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "退款申请仅面向学生账号");
        }
        RefundIntentView view = refundIntentService.apply(userId, orderId,
                request == null ? null : request.reason());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.ok(view));
    }

    @GetMapping("/refund-applications/{requestId}")
    public ApiResponse<RefundIntentView> get(
            @PathVariable("requestId") String requestId,
            @RequestAttribute(StudentAuthInterceptor.USER_ID_ATTRIBUTE) Long userId) {
        return ApiResponse.ok(refundIntentService.get(userId, requestId));
    }
}