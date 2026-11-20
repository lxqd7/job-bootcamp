package com.jobbootcamp.trade.domain.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record CreateOrderRequest(
        @NotBlank String quoteId,
        @NotBlank String quoteHash,
        List<Long> selectedCouponIds
) {
}