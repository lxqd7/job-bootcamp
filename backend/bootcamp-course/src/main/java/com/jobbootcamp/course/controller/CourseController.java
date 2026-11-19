package com.jobbootcamp.course.controller;

import com.jobbootcamp.common.web.ApiResponse;
import com.jobbootcamp.course.application.CourseService;
import com.jobbootcamp.course.domain.dto.CourseDetail;
import com.jobbootcamp.course.domain.dto.CourseSummary;
import com.jobbootcamp.course.domain.dto.PageResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/courses")
public class CourseController {

    private final CourseService courseService;

    public CourseController(CourseService courseService) {
        this.courseService = courseService;
    }

    @GetMapping
    public ApiResponse<PageResponse<CourseSummary>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResponse.ok(courseService.publicList(keyword, categoryId, page, pageSize));
    }

    @GetMapping("/{id}")
    public ApiResponse<CourseDetail> detail(@PathVariable("id") Long id) {
        return ApiResponse.ok(courseService.publicDetail(id));
    }
}