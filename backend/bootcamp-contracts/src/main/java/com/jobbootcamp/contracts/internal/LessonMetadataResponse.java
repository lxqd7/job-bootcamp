package com.jobbootcamp.contracts.internal;

import java.util.List;

public record LessonMetadataResponse(
        Long courseId,
        Long lessonId,
        Long mediaId,
        Long durationMs,
        Long catalogVersion,
        Long totalCount,
        String status,
        String title,
        List<Long> activeLessonIds
) {
}