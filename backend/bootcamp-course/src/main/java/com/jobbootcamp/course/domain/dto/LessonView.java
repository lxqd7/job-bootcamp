package com.jobbootcamp.course.domain.dto;

import java.util.List;

public record LessonView(String id, String title, int sort, boolean hasVideo) {
}