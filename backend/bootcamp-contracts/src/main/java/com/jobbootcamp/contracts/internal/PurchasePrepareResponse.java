package com.jobbootcamp.contracts.internal;

import java.util.List;

public record PurchasePrepareResponse(Long orderId, Long sourceVersion, List<SourceSelectionItem> holds) {
}