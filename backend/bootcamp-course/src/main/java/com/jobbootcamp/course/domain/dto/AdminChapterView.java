package com.jobbootcamp.course.domain.dto;

import java.util.List;

public record AdminChapterView(
        String id,
        String title,
        int sort,
        long revision,
        List<AdminLessonView> lessons
) {
}