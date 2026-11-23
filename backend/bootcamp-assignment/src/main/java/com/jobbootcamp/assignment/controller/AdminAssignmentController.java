package com.jobbootcamp.assignment.controller;

import com.jobbootcamp.assignment.application.AdminAssignmentService;
import com.jobbootcamp.assignment.config.AssignmentAuthInterceptor;
import com.jobbootcamp.assignment.domain.dto.AssignmentRequest;
import com.jobbootcamp.assignment.domain.dto.AssignmentSummary;
import com.jobbootcamp.assignment.domain.dto.AssignmentView;
import com.jobbootcamp.assignment.domain.dto.PageResponse;
import com.jobbootcamp.common.error.BusinessException;
import com.jobbootcamp.common.error.ErrorCode;
import com.jobbootcamp.common.web.ApiResponse;
import org.springframework.web.bind.annotation.DeleteMapping;
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
@RequestMapping("/api/v1/admin/assignments")
public class AdminAssignmentController {

    private final AdminAssignmentService service;

    public AdminAssignmentController(AdminAssignmentService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<PageResponse<AssignmentSummary>> list(
            @RequestParam(required = false) Long courseId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestAttribute(AssignmentAuthInterceptor.ROLE_ATTRIBUTE) String role) {
        requireAdmin(role);
        return ApiResponse.ok(service.list(courseId, status, page, pageSize));
    }

    @PostMapping
    public ApiResponse<AssignmentView> create(
            @RequestBody AssignmentRequest request,
            @RequestAttribute(AssignmentAuthInterceptor.USER_ID_ATTRIBUTE) Long adminId,
            @RequestAttribute(AssignmentAuthInterceptor.ROLE_ATTRIBUTE) String role) {
        requireAdmin(role);
        return ApiResponse.ok(service.create(adminId, request));
    }

    @GetMapping("/{id}")
    public ApiResponse<AssignmentView> get(
            @PathVariable("id") Long id,
            @RequestAttribute(AssignmentAuthInterceptor.ROLE_ATTRIBUTE) String role) {
        requireAdmin(role);
        return ApiResponse.ok(service.get(id));
    }

    @PatchMapping("/{id}")
    public ApiResponse<AssignmentView> update(
            @PathVariable("id") Long id,
            @RequestBody AssignmentRequest request,
            @RequestAttribute(AssignmentAuthInterceptor.ROLE_ATTRIBUTE) String role) {
        requireAdmin(role);
        return ApiResponse.ok(service.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(
            @PathVariable("id") Long id,
            @RequestParam("revision") Long revision,
            @RequestAttribute(AssignmentAuthInterceptor.ROLE_ATTRIBUTE) String role) {
        requireAdmin(role);
        service.delete(id, revision);
        return ApiResponse.ok(null);
    }

    @PostMapping("/{id}/publish")
    public ApiResponse<AssignmentView> publish(
            @PathVariable("id") Long id,
            @RequestBody RevisionRequest request,
            @RequestAttribute(AssignmentAuthInterceptor.ROLE_ATTRIBUTE) String role) {
        requireAdmin(role);
        return ApiResponse.ok(service.publish(id, request == null ? null : request.revision()));
    }

    @PostMapping("/{id}/offline")
    public ApiResponse<AssignmentView> offline(
            @PathVariable("id") Long id,
            @RequestBody RevisionRequest request,
            @RequestAttribute(AssignmentAuthInterceptor.ROLE_ATTRIBUTE) String role) {
        requireAdmin(role);
        return ApiResponse.ok(service.offline(id, request == null ? null : request.revision()));
    }

    private void requireAdmin(String role) {
        if (!"ADMIN".equals(role)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "正式作业仅管理员可维护");
        }
    }

    public record RevisionRequest(Long revision) {
    }
}