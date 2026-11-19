package com.jobbootcamp.course.controller;

import com.jobbootcamp.common.web.ApiResponse;
import com.jobbootcamp.course.application.TeacherAssignmentService;
import com.jobbootcamp.course.domain.dto.TeacherAssignmentsRequest;
import com.jobbootcamp.course.domain.dto.TeacherAssignmentsResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/teachers")
public class AdminTeacherController {

    private final TeacherAssignmentService teacherAssignmentService;

    public AdminTeacherController(TeacherAssignmentService teacherAssignmentService) {
        this.teacherAssignmentService = teacherAssignmentService;
    }

    @GetMapping("/{id}/assignments")
    public ApiResponse<TeacherAssignmentsResponse> assignments(@PathVariable("id") Long id) {
        return ApiResponse.ok(teacherAssignmentService.assignments(id));
    }

    @PutMapping("/{id}/assignments")
    public ApiResponse<TeacherAssignmentsResponse> assign(@PathVariable("id") Long id,
                                                          @Valid @RequestBody TeacherAssignmentsRequest request) {
        return ApiResponse.ok(teacherAssignmentService.assign(id, request));
    }
}