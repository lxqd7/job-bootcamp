package com.jobbootcamp.contracts.internal;

import java.util.List;

public record PricingCalculateResponse(
        String quoteId,
        String status,
        String pricingHash,
        long totalCent,
        long creditCent,
        long discountCent,
        long payableCent,
        List<PricingCouponPlan> couponPlan,
        String errorCode,
        String errorMessage
) {
}