package com.jobbootcamp.course.controller;

import com.jobbootcamp.common.error.BusinessException;
import com.jobbootcamp.common.error.ErrorCode;
import com.jobbootcamp.common.web.ApiResponse;
import com.jobbootcamp.course.application.FreeClaimService;
import com.jobbootcamp.course.config.StudentAuthInterceptor;
import com.jobbootcamp.course.domain.dto.ClaimView;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class ClaimController {

    private final FreeClaimService freeClaimService;

    public ClaimController(FreeClaimService freeClaimService) {
        this.freeClaimService = freeClaimService;
    }

    @PostMapping("/courses/{id}/claim")
    public ApiResponse<ClaimView> claim(
            @PathVariable("id") Long courseId,
            @RequestAttribute(StudentAuthInterceptor.USER_ID_ATTRIBUTE) Long userId,
            @RequestAttribute(StudentAuthInterceptor.ROLE_ATTRIBUTE) String role) {
        requireStudent(role);
        return ApiResponse.ok(freeClaimService.claim(userId, courseId));
    }

    @GetMapping("/claims/{claimId}")
    public ApiResponse<ClaimView> claimStatus(
            @PathVariable("claimId") String claimId,
            @RequestAttribute(StudentAuthInterceptor.USER_ID_ATTRIBUTE) Long userId,
            @RequestAttribute(StudentAuthInterceptor.ROLE_ATTRIBUTE) String role) {
        requireStudent(role);
        return ApiResponse.ok(freeClaimService.getClaim(userId, claimId));
    }

    private void requireStudent(String role) {
        if (!"STUDENT".equals(role)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "免费领取仅面向学生账号");
        }
    }
}