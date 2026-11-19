package com.jobbootcamp.course.controller;

import com.jobbootcamp.common.web.ApiResponse;
import com.jobbootcamp.course.application.CourseService;
import com.jobbootcamp.course.domain.dto.AdminChapterView;
import com.jobbootcamp.course.domain.dto.AdminCourseDetail;
import com.jobbootcamp.course.domain.dto.AdminCourseItem;
import com.jobbootcamp.course.domain.dto.AdminLessonView;
import com.jobbootcamp.course.domain.dto.ChapterRequest;
import com.jobbootcamp.course.domain.dto.CourseCreateRequest;
import com.jobbootcamp.course.domain.dto.CourseUpdateRequest;
import com.jobbootcamp.course.domain.dto.LessonRequest;
import com.jobbootcamp.course.domain.dto.PageResponse;
import com.jobbootcamp.course.domain.dto.PublishRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminCourseController {

    private final CourseService courseService;

    public AdminCourseController(CourseService courseService) {
        this.courseService = courseService;
    }

    @GetMapping("/courses")
    public ApiResponse<PageResponse<AdminCourseItem>> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResponse.ok(courseService.adminList(status, keyword, page, pageSize));
    }

    @GetMapping("/courses/{id}")
    public ApiResponse<AdminCourseDetail> detail(@PathVariable("id") Long id) {
        return ApiResponse.ok(courseService.adminDetail(id));
    }

    @PostMapping("/courses")
    public ApiResponse<AdminCourseItem> create(@Valid @RequestBody CourseCreateRequest request) {
        return ApiResponse.ok(courseService.create(request));
    }

    @PatchMapping("/courses/{id}")
    public ApiResponse<AdminCourseItem> update(@PathVariable("id") Long id,
                                               @Valid @RequestBody CourseUpdateRequest request) {
        return ApiResponse.ok(courseService.update(id, request));
    }

    @PostMapping("/courses/{id}/publish")
    public ApiResponse<AdminCourseItem> publish(@PathVariable("id") Long id,
                                                @Valid @RequestBody PublishRequest request) {
        return ApiResponse.ok(courseService.publish(id, request.revision()));
    }

    @PostMapping("/courses/{id}/offline")
    public ApiResponse<AdminCourseItem> offline(@PathVariable("id") Long id,
                                                @Valid @RequestBody PublishRequest request) {
        return ApiResponse.ok(courseService.offline(id, request.revision()));
    }

    @PostMapping("/courses/{courseId}/chapters")
    public ApiResponse<AdminChapterView> createChapter(@PathVariable("courseId") Long courseId,
                                                       @Valid @RequestBody ChapterRequest request) {
        return ApiResponse.ok(courseService.createChapter(courseId, request));
    }

    @PatchMapping("/courses/{courseId}/chapters/{chapterId}")
    public ApiResponse<AdminChapterView> updateChapter(@PathVariable("courseId") Long courseId,
                                                       @PathVariable("chapterId") Long chapterId,
                                                       @Valid @RequestBody ChapterRequest request) {
        return ApiResponse.ok(courseService.updateChapter(courseId, chapterId, request));
    }

    @DeleteMapping("/chapters/{chapterId}")
    public ApiResponse<Void> deleteChapter(@PathVariable("chapterId") Long chapterId,
                                           @RequestParam("revision") Long revision) {
        courseService.deleteChapter(chapterId, revision);
        return ApiResponse.ok(null);
    }

    @PostMapping("/chapters/{chapterId}/lessons")
    public ApiResponse<AdminLessonView> createLesson(@PathVariable("chapterId") Long chapterId,
                                                     @Valid @RequestBody LessonRequest request) {
        return ApiResponse.ok(courseService.createLesson(chapterId, request));
    }

    @PatchMapping("/chapters/{chapterId}/lessons/{lessonId}")
    public ApiResponse<AdminLessonView> updateLesson(@PathVariable("chapterId") Long chapterId,
                                                     @PathVariable("lessonId") Long lessonId,
                                                     @Valid @RequestBody LessonRequest request) {
        return ApiResponse.ok(courseService.updateLesson(chapterId, lessonId, request));
    }

    @DeleteMapping("/lessons/{lessonId}")
    public ApiResponse<Void> deleteLesson(@PathVariable("lessonId") Long lessonId,
                                          @RequestParam("revision") Long revision) {
        courseService.deleteLessonByLessonId(lessonId, revision);
        return ApiResponse.ok(null);
    }
}