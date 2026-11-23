package com.jobbootcamp.assignment.controller;

import com.jobbootcamp.assignment.application.StudentAssignmentService;
import com.jobbootcamp.assignment.config.AssignmentAuthInterceptor;
import com.jobbootcamp.assignment.domain.dto.AssignmentProgressView;
import com.jobbootcamp.assignment.domain.dto.AttemptView;
import com.jobbootcamp.assignment.domain.dto.DraftRequest;
import com.jobbootcamp.assignment.domain.dto.PageResponse;
import com.jobbootcamp.assignment.domain.dto.ReviewView;
import com.jobbootcamp.assignment.domain.dto.StudentAssignmentView;
import com.jobbootcamp.assignment.domain.dto.SubmissionRequest;
import com.jobbootcamp.assignment.domain.dto.SubmissionSummary;
import com.jobbootcamp.assignment.domain.dto.SubmissionView;
import com.jobbootcamp.common.error.BusinessException;
import com.jobbootcamp.common.error.ErrorCode;
import com.jobbootcamp.common.web.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class StudentAssignmentController {

    private final StudentAssignmentService service;

    public StudentAssignmentController(StudentAssignmentService service) {
        this.service = service;
    }

    @GetMapping("/courses/{courseId}/assignments")
    public ApiResponse<List<StudentAssignmentView>> courseAssignments(
            @PathVariable("courseId") Long courseId,
            @RequestAttribute(AssignmentAuthInterceptor.USER_ID_ATTRIBUTE) Long userId,
            @RequestAttribute(AssignmentAuthInterceptor.ROLE_ATTRIBUTE) String role) {
        requireStudent(role);
        return ApiResponse.ok(service.courseAssignments(userId, courseId));
    }

    @GetMapping("/assignments/{id}")
    public ApiResponse<StudentAssignmentView> getAssignment(
            @PathVariable("id") Long id,
            @RequestAttribute(AssignmentAuthInterceptor.USER_ID_ATTRIBUTE) Long userId,
            @RequestAttribute(AssignmentAuthInterceptor.ROLE_ATTRIBUTE) String role) {
        requireStudent(role);
        return ApiResponse.ok(service.getAssignment(userId, id));
    }

    @GetMapping("/assignments/{id}/progress")
    public ApiResponse<AssignmentProgressView> progress(
            @PathVariable("id") Long id,
            @RequestAttribute(AssignmentAuthInterceptor.USER_ID_ATTRIBUTE) Long userId,
            @RequestAttribute(AssignmentAuthInterceptor.ROLE_ATTRIBUTE) String role) {
        requireStudent(role);
        return ApiResponse.ok(service.progress(userId, id));
    }

    @PostMapping("/assignments/{id}/attempts")
    public ApiResponse<AttemptView> startAttempt(
            @PathVariable("id") Long id,
            @RequestAttribute(AssignmentAuthInterceptor.USER_ID_ATTRIBUTE) Long userId,
            @RequestAttribute(AssignmentAuthInterceptor.ROLE_ATTRIBUTE) String role) {
        requireStudent(role);
        return ApiResponse.ok(service.startAttempt(userId, id));
    }

    @GetMapping("/attempts/{id}")
    public ApiResponse<AttemptView> getAttempt(
            @PathVariable("id") Long id,
            @RequestAttribute(AssignmentAuthInterceptor.USER_ID_ATTRIBUTE) Long userId,
            @RequestAttribute(AssignmentAuthInterceptor.ROLE_ATTRIBUTE) String role) {
        requireStudent(role);
        return ApiResponse.ok(service.getAttempt(userId, id));
    }

    @PutMapping("/attempts/{id}/draft")
    public ApiResponse<AttemptView> saveDraft(
            @PathVariable("id") Long id,
            @RequestBody DraftRequest request,
            @RequestAttribute(AssignmentAuthInterceptor.USER_ID_ATTRIBUTE) Long userId,
            @RequestAttribute(AssignmentAuthInterceptor.ROLE_ATTRIBUTE) String role) {
        requireStudent(role);
        return ApiResponse.ok(service.saveDraft(userId, id,
                request == null ? null : request.answers(),
                request == null ? null : request.draftRevision()));
    }

    @PostMapping("/attempts/{id}/submissions")
    public ApiResponse<SubmissionView> submit(
            @PathVariable("id") Long id,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody(required = false) SubmissionRequest request,
            @RequestAttribute(AssignmentAuthInterceptor.USER_ID_ATTRIBUTE) Long userId,
            @RequestAttribute(AssignmentAuthInterceptor.ROLE_ATTRIBUTE) String role) {
        requireStudent(role);
        return ApiResponse.ok(service.submit(userId, id, idempotencyKey,
                request == null ? null : request.answers()));
    }

    @GetMapping("/assignments/{id}/submissions")
    public ApiResponse<PageResponse<SubmissionSummary>> submissions(
            @PathVariable("id") Long id,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestAttribute(AssignmentAuthInterceptor.USER_ID_ATTRIBUTE) Long userId,
            @RequestAttribute(AssignmentAuthInterceptor.ROLE_ATTRIBUTE) String role) {
        requireStudent(role);
        return ApiResponse.ok(service.listSubmissions(userId, id, page, pageSize));
    }

    @GetMapping("/submissions/{id}")
    public ApiResponse<SubmissionView> submission(
            @PathVariable("id") Long id,
            @RequestAttribute(AssignmentAuthInterceptor.USER_ID_ATTRIBUTE) Long userId,
            @RequestAttribute(AssignmentAuthInterceptor.ROLE_ATTRIBUTE) String role) {
        requireStudent(role);
        return ApiResponse.ok(service.getSubmission(userId, id));
    }

    @GetMapping("/submissions/{id}/review")
    public ApiResponse<ReviewView> review(
            @PathVariable("id") Long id,
            @RequestAttribute(AssignmentAuthInterceptor.USER_ID_ATTRIBUTE) Long userId,
            @RequestAttribute(AssignmentAuthInterceptor.ROLE_ATTRIBUTE) String role) {
        requireStudent(role);
        return ApiResponse.ok(service.getReview(userId, id));
    }

    private void requireStudent(String role) {
        if (!"STUDENT".equals(role)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "学生作答接口仅面向学生账号");
        }
    }
}