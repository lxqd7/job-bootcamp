package com.jobbootcamp.marketing.domain.dto;

public record ActivityItemRequest(Long ruleId, Integer stockTotal, Integer perUserLimit,
                                  Boolean enabled, Long revision) {
}