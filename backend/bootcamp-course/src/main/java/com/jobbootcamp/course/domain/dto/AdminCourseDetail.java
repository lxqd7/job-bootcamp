package com.jobbootcamp.course.domain.dto;

import java.util.List;

public record AdminCourseDetail(
        String id,
        String title,
        String description,
        long priceCent,
        boolean isFree,
        String status,
        String categoryId,
        String coverUrl,
        long catalogVersion,
        long revision,
        List<AdminChapterView> chapters
) {
}