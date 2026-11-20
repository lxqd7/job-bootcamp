package com.jobbootcamp.contracts.internal;

import java.util.List;

public record CourseCatalogResponse(
        Long courseId,
        String title,
        String description,
        Long catalogVersion,
        List<CatalogChapter> chapters
) {
    public record CatalogChapter(Long id, String title, int sort, List<CatalogLesson> lessons) {
    }

    public record CatalogLesson(Long id, Long chapterId, String title, int sort,
                                Long mediaId, Long durationMs) {
    }
}