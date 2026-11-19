package com.jobbootcamp.course.controller;

import com.jobbootcamp.common.web.ApiResponse;
import com.jobbootcamp.course.application.TeacherAssignmentService;
import com.jobbootcamp.course.domain.dto.TeacherCourseItem;
import com.jobbootcamp.course.domain.dto.TeacherRouteItem;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/teacher")
public class TeacherController {

    private final TeacherAssignmentService teacherAssignmentService;

    public TeacherController(TeacherAssignmentService teacherAssignmentService) {
        this.teacherAssignmentService = teacherAssignmentService;
    }

    @GetMapping("/courses")
    public ApiResponse<List<TeacherCourseItem>> courses(
            @RequestAttribute("currentTeacherId") Long currentTeacherId) {
        return ApiResponse.ok(teacherAssignmentService.courses(currentTeacherId));
    }

    @GetMapping("/routes")
    public ApiResponse<List<TeacherRouteItem>> routes(
            @RequestAttribute("currentTeacherId") Long currentTeacherId) {
        return ApiResponse.ok(teacherAssignmentService.routes(currentTeacherId));
    }
}