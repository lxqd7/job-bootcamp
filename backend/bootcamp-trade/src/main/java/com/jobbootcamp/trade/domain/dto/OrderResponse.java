package com.jobbootcamp.trade.domain.dto;

import java.time.LocalDateTime;
import java.util.List;

public record OrderResponse(
        Long id,
        String orderNo,
        String state,
        String fulfillmentState,
        LocalDateTime createdAt,
        LocalDateTime expiresAt,
        LocalDateTime paidAt,
        Pricing pricing,
        List<Item> items,
        List<QuoteResponse.CouponLine> coupons,
        String mode
) {
    public record Pricing(long totalCent, long creditCent, long discountCent, long payableCent) {
    }

    public record Item(Long courseId, Long courseVersion, long unitPriceCent, long allocatedCent,
                       long creditCent, long couponCent, long payableCent, boolean free, boolean newPaid) {
    }
}