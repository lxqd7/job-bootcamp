package com.jobbootcamp.learning.domain.dto;

import java.time.LocalDateTime;
import java.util.List;

public record LearningCourseResponse(
        String courseId,
        String title,
        String description,
        long catalogVersion,
        long totalCount,
        long completedCount,
        List<LearningChapter> chapters
) {
    public record LearningChapter(Long id, String title, int sort, List<LearningLesson> lessons) {
    }

    public record LearningLesson(Long id, String title, int sort, Long mediaId, Long durationMs,
                                 long positionMs, long maxPositionMs, boolean completed,
                                 LocalDateTime completedAt) {
    }
}