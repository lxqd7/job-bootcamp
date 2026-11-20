package com.jobbootcamp.contracts.internal;

import java.util.List;

public record ProductSnapshotResponse(
        String type,
        Long id,
        String title,
        String description,
        Long version,
        Long priceCent,
        boolean free,
        String status,
        Long categoryId,
        List<SnapshotLesson> lessons
) {
    public record SnapshotLesson(Long lessonId, Long chapterId, String title, int sort,
                                 Long mediaId, Long durationMs) {
    }
}