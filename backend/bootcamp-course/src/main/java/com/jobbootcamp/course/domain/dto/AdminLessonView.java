package com.jobbootcamp.course.domain.dto;

public record AdminLessonView(
        String id,
        String title,
        int sort,
        String mediaId,
        long revision
) {
}