package com.jobbootcamp.assignment.controller;

import com.jobbootcamp.assignment.application.TeacherAssignmentService;
import com.jobbootcamp.assignment.config.AssignmentAuthInterceptor;
import com.jobbootcamp.assignment.domain.dto.PageResponse;
import com.jobbootcamp.assignment.domain.dto.TeacherAssignmentItem;
import com.jobbootcamp.assignment.domain.dto.TeacherSubmissionSummary;
import com.jobbootcamp.assignment.domain.dto.TeacherSubmissionView;
import com.jobbootcamp.common.error.BusinessException;
import com.jobbootcamp.common.error.ErrorCode;
import com.jobbootcamp.common.web.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/teacher")
public class TeacherAssignmentController {

    private final TeacherAssignmentService service;

    public TeacherAssignmentController(TeacherAssignmentService service) {
        this.service = service;
    }

    @GetMapping("/courses/{courseId}/assignments")
    public ApiResponse<List<TeacherAssignmentItem>> assignments(
            @PathVariable("courseId") Long courseId,
            @RequestAttribute(AssignmentAuthInterceptor.USER_ID_ATTRIBUTE) Long userId,
            @RequestAttribute(AssignmentAuthInterceptor.ROLE_ATTRIBUTE) String role) {
        requireTeacher(role);
        return ApiResponse.ok(service.assignments(userId, courseId));
    }

    @GetMapping("/courses/{courseId}/submissions")
    public ApiResponse<PageResponse<TeacherSubmissionSummary>> submissions(
            @PathVariable("courseId") Long courseId,
            @RequestParam(required = false) Long assignmentId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestAttribute(AssignmentAuthInterceptor.USER_ID_ATTRIBUTE) Long userId,
            @RequestAttribute(AssignmentAuthInterceptor.ROLE_ATTRIBUTE) String role) {
        requireTeacher(role);
        return ApiResponse.ok(service.submissions(userId, courseId, assignmentId, page, pageSize));
    }

    @GetMapping("/submissions/{id}")
    public ApiResponse<TeacherSubmissionView> submission(
            @PathVariable("id") Long id,
            @RequestAttribute(AssignmentAuthInterceptor.USER_ID_ATTRIBUTE) Long userId,
            @RequestAttribute(AssignmentAuthInterceptor.ROLE_ATTRIBUTE) String role) {
        requireTeacher(role);
        return ApiResponse.ok(service.submission(userId, id));
    }

    private void requireTeacher(String role) {
        if (!"TEACHER".equals(role)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "教师查看接口仅面向教师账号");
        }
    }
}