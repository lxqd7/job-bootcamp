package com.jobbootcamp.trade.domain.dto;

import java.util.List;

public record QuoteResponse(
        String quoteId,
        String status,
        java.time.LocalDateTime expiresAt,
        String quoteHash,
        Product product,
        List<CourseLine> courses,
        List<CouponLine> couponPlan,
        long totalCent,
        long creditCent,
        long discountCent,
        long payableCent,
        List<String> refundDependencyWarnings
) {
    public record Product(String type, Long id, String title, Long version) {
    }

    public record CourseLine(Long courseId, long unitPriceCent, long allocatedCent, long creditCent,
                             Long sourceId, Long sourceGrantId, Long sourceOrderId, long remainingCent) {
    }

    public record CouponLine(Long userCouponId, int applyOrder, long baseCent, long discountCent,
                             List<Allocation> allocation) {
    }

    public record Allocation(Long courseId, long discountCent) {
    }
}