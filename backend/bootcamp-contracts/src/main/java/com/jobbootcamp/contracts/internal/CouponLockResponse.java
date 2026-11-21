package com.jobbootcamp.contracts.internal;

import java.util.List;

public record CouponLockResponse(
        Long orderId,
        String state,
        String pricingHash,
        List<CouponLockLine> coupons
) {
}