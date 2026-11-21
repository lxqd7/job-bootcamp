package com.jobbootcamp.contracts.internal;

import java.util.List;

public record OrderStatusBatchResponse(List<OrderStatusItem> orders) {
    public record OrderStatusItem(Long orderId, String state) {
    }
}