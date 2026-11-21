package com.jobbootcamp.contracts.internal;

import java.util.List;

public record PricingCouponSelection(String mode, List<Long> userCouponIds) {
}