package com.jobbootcamp.contracts.internal;

import java.util.List;

public record RefundBasisResponse(
        Long orderId,
        String productType,
        String paidAt,
        String snapshotHash,
        long paidCent,
        List<CourseLessons> courses
) {
    public record CourseLessons(Long courseId, List<Long> lessonIds) {
    }
}