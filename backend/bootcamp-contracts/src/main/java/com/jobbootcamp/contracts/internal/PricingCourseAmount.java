package com.jobbootcamp.contracts.internal;

import java.util.List;

public record PricingCourseAmount(Long courseId, Long remainingCent, List<Long> categoryIds) {
}