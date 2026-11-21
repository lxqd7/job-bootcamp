package com.jobbootcamp.contracts.internal;

import java.util.List;

public record SourceSelectionRequest(Long userId, List<Long> courseIds) {
}