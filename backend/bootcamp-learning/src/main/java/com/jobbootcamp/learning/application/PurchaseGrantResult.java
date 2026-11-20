package com.jobbootcamp.learning.application;

import java.util.List;

public record PurchaseGrantResult(Long sourceId, List<Grant> grants) {

    public record Grant(Long courseId, Long grantId) {
    }
}