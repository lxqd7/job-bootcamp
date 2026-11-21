package com.jobbootcamp.contracts.internal;

import java.util.List;

public record OrderStatusBatchRequest(List<Long> orderIds) {
}