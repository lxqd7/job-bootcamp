package com.jobbootcamp.contracts.internal;

import java.util.List;

public record CouponLockLine(
        Long userCouponId,
        int applyOrder,
        long baseCent,
        long discountCent,
        List<PricingCouponAllocation> allocation
) {
}