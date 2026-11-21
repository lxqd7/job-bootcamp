package com.jobbootcamp.contracts.internal;

import java.util.List;

public record PricingCalculateRequest(
        String quoteId,
        Long userId,
        String productType,
        Long productId,
        Long routeId,
        long totalCent,
        long creditCent,
        List<PricingCourseAmount> courses,
        PricingCouponSelection couponSelection
) {
}