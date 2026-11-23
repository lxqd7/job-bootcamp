package com.jobbootcamp.course.controller;

import com.jobbootcamp.contracts.internal.TeacherBenefitScopeRequest;
import com.jobbootcamp.contracts.internal.TeacherBenefitScopeResponse;
import com.jobbootcamp.contracts.internal.TeacherScopeCheckRequest;
import com.jobbootcamp.contracts.internal.TeacherScopeCheckResponse;
import com.jobbootcamp.course.application.TeacherAssignmentService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/int/v1/teachers")
public class InternalTeacherController {

    private final TeacherAssignmentService teacherAssignmentService;

    public InternalTeacherController(TeacherAssignmentService teacherAssignmentService) {
        this.teacherAssignmentService = teacherAssignmentService;
    }

    @PostMapping("/scope")
    public TeacherScopeCheckResponse scope(@RequestBody TeacherScopeCheckRequest request) {
        return new TeacherScopeCheckResponse(
                teacherAssignmentService.hasCourseScope(request.teacherId(), request.courseId()));
    }

    @PostMapping("/benefit-scope")
    public TeacherBenefitScopeResponse benefitScope(@RequestBody TeacherBenefitScopeRequest request) {
        return teacherAssignmentService.benefitScope(
                request.teacherId(), request.courseId(), request.routeIds());
    }
}