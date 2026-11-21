package com.jobbootcamp.contracts.internal;

public record CouponLockStateRequest(
        Long orderId,
        Long userId,
        String pricingHash,
        String confirmedOrderState
) {
}