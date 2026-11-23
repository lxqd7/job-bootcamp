package com.jobbootcamp.assignment.controller;

import com.jobbootcamp.assignment.application.BenefitAssignmentService;
import com.jobbootcamp.assignment.config.AssignmentAuthInterceptor;
import com.jobbootcamp.assignment.domain.dto.BenefitAssignmentRequest;
import com.jobbootcamp.assignment.domain.dto.BenefitAssignmentSummary;
import com.jobbootcamp.assignment.domain.dto.BenefitAssignmentView;
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
@RequestMapping("/api/v1/teacher/benefit-assignments")
public class TeacherBenefitController {

    private final BenefitAssignmentService service;

    public TeacherBenefitController(BenefitAssignmentService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<PageResponse<BenefitAssignmentSummary>> list(
            @RequestParam Long courseId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestAttribute(AssignmentAuthInterceptor.USER_ID_ATTRIBUTE) Long teacherId,
            @RequestAttribute(AssignmentAuthInterceptor.ROLE_ATTRIBUTE) String role) {
        requireTeacher(role);
        return ApiResponse.ok(service.list(teacherId, courseId, page, pageSize));
    }

    @PostMapping
    public ApiResponse<BenefitAssignmentView> create(
            @RequestBody BenefitAssignmentRequest request,
            @RequestAttribute(AssignmentAuthInterceptor.USER_ID_ATTRIBUTE) Long teacherId,
            @RequestAttribute(AssignmentAuthInterceptor.ROLE_ATTRIBUTE) String role) {
        requireTeacher(role);
        return ApiResponse.ok(service.create(teacherId, request));
    }

    @GetMapping("/{id}")
    public ApiResponse<BenefitAssignmentView> get(
            @PathVariable("id") Long id,
            @RequestAttribute(AssignmentAuthInterceptor.USER_ID_ATTRIBUTE) Long teacherId,
            @RequestAttribute(AssignmentAuthInterceptor.ROLE_ATTRIBUTE) String role) {
        requireTeacher(role);
        return ApiResponse.ok(service.get(teacherId, id));
    }

    @PatchMapping("/{id}")
    public ApiResponse<BenefitAssignmentView> update(
            @PathVariable("id") Long id,
            @RequestBody BenefitAssignmentRequest request,
            @RequestAttribute(AssignmentAuthInterceptor.USER_ID_ATTRIBUTE) Long teacherId,
            @RequestAttribute(AssignmentAuthInterceptor.ROLE_ATTRIBUTE) String role) {
        requireTeacher(role);
        return ApiResponse.ok(service.update(teacherId, id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(
            @PathVariable("id") Long id,
            @RequestParam("revision") Long revision,
            @RequestAttribute(AssignmentAuthInterceptor.USER_ID_ATTRIBUTE) Long teacherId,
            @RequestAttribute(AssignmentAuthInterceptor.ROLE_ATTRIBUTE) String role) {
        requireTeacher(role);
        service.delete(teacherId, id, revision);
        return ApiResponse.ok(null);
    }

    @PostMapping("/{id}/publish")
    public ApiResponse<BenefitAssignmentView> publish(
            @PathVariable("id") Long id,
            @RequestBody RevisionRequest request,
            @RequestAttribute(AssignmentAuthInterceptor.USER_ID_ATTRIBUTE) Long teacherId,
            @RequestAttribute(AssignmentAuthInterceptor.ROLE_ATTRIBUTE) String role) {
        requireTeacher(role);
        return ApiResponse.ok(service.publish(teacherId, id, request == null ? null : request.revision()));
    }

    @PostMapping("/{id}/offline")
    public ApiResponse<BenefitAssignmentView> offline(
            @PathVariable("id") Long id,
            @RequestBody RevisionRequest request,
            @RequestAttribute(AssignmentAuthInterceptor.USER_ID_ATTRIBUTE) Long teacherId,
            @RequestAttribute(AssignmentAuthInterceptor.ROLE_ATTRIBUTE) String role) {
        requireTeacher(role);
        return ApiResponse.ok(service.offline(teacherId, id, request == null ? null : request.revision()));
    }

    private void requireTeacher(String role) {
        if (!"TEACHER".equals(role)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "福利题只能由教师维护");
        }
    }

    public record RevisionRequest(Long revision) {
    }
}