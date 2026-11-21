package com.jobbootcamp.contracts.internal;

import java.util.List;

public record CouponLockPrepareRequest(
        Long orderId,
        Long userId,
        String quoteId,
        String pricingHash,
        List<Long> couponIds
) {
}