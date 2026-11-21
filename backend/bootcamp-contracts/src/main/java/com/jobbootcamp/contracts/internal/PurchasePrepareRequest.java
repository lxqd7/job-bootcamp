package com.jobbootcamp.contracts.internal;

import java.util.List;

public record PurchasePrepareRequest(Long orderId, Long userId, List<Long> courseIds) {
}