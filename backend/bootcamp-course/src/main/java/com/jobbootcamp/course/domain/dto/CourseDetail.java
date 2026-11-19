package com.jobbootcamp.course.domain.dto;

import java.util.List;

public record CourseDetail(
        String id,
        String title,
        String description,
        long priceCent,
        boolean isFree,
        String status,
        String categoryId,
        String coverUrl,
        long catalogVersion,
        boolean isOwned,
        List<ChapterView> chapters
) {
}