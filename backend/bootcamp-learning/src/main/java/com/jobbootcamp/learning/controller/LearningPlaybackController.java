package com.jobbootcamp.learning.controller;

import com.jobbootcamp.common.error.BusinessException;
import com.jobbootcamp.common.error.ErrorCode;
import com.jobbootcamp.common.web.ApiResponse;
import com.jobbootcamp.contracts.internal.PlayAuthorizationResponse;
import com.jobbootcamp.learning.application.LearningPlaybackService;
import com.jobbootcamp.learning.config.StudentAuthInterceptor;
import com.jobbootcamp.learning.domain.dto.ProgressRequest;
import com.jobbootcamp.learning.domain.dto.ProgressResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class LearningPlaybackController {

    private final LearningPlaybackService playbackService;

    public LearningPlaybackController(LearningPlaybackService playbackService) {
        this.playbackService = playbackService;
    }

    @PostMapping("/lessons/{id}/play-authorizations")
    public ApiResponse<PlayAuthorizationResponse> authorize(
            @PathVariable("id") Long lessonId,
            @RequestAttribute(StudentAuthInterceptor.USER_ID_ATTRIBUTE) Long userId,
            @RequestAttribute(StudentAuthInterceptor.ROLE_ATTRIBUTE) String role,
            @RequestAttribute(StudentAuthInterceptor.SESSION_ID_ATTRIBUTE) String sessionId) {
        requireStudent(role);
        return ApiResponse.ok(playbackService.authorize(userId, Long.valueOf(sessionId), lessonId));
    }

    @PutMapping("/learning/lessons/{id}/progress")
    public ApiResponse<ProgressResponse> progress(
            @PathVariable("id") Long lessonId,
            @RequestAttribute(StudentAuthInterceptor.USER_ID_ATTRIBUTE) Long userId,
            @RequestAttribute(StudentAuthInterceptor.ROLE_ATTRIBUTE) String role,
            @Valid @RequestBody ProgressRequest request) {
        requireStudent(role);
        return ApiResponse.ok(playbackService.updateProgress(userId, lessonId, request));
    }

    private void requireStudent(String role) {
        if (!"STUDENT".equals(role)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "播放与学习进度仅面向学生账号");
        }
    }
}