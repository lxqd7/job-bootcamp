package com.jobbootcamp.contracts.internal;

import java.util.List;

public record TeacherBenefitScopeRequest(Long teacherId, Long courseId, List<Long> routeIds) {
}