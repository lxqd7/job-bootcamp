package com.jobbootcamp.learning.application;

public record RoutePurchaseMember(Long courseId, Long courseVersion, Long epochId,
                                  boolean free, boolean newPaid) {
}