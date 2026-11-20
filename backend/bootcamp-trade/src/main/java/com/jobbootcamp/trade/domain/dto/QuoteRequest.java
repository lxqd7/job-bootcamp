package com.jobbootcamp.trade.domain.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record QuoteRequest(
        @NotBlank String productType,
        @NotNull @Min(1) Long productId,
        @Valid CouponSelection couponSelection
) {
    public record CouponSelection(String mode, List<Long> ids) {
    }
}