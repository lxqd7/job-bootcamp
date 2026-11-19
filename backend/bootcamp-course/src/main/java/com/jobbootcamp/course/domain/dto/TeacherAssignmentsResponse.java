package com.jobbootcamp.course.domain.dto;

import java.util.List;

public record TeacherAssignmentsResponse(
        String teacherId,
        List<String> courseIds,
        List<String> routeIds,
        long revision
) {
}