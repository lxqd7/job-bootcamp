package com.jobbootcamp.course.domain.dto;

import java.util.List;

public record ChapterView(String id, String title, int sort, List<LessonView> lessons) {
}