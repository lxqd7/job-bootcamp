package com.jobbootcamp.contracts.internal;

/**
 * 目标课程可用于抵扣的最早 ACTIVE 来源；sourceOrderId 仅付费来源有值。
 */
public record SourceSelectionItem(
        Long courseId,
        Long sourceId,
        Long sourceGrantId,
        String sourceType,
        Long sourceOrderId,
        String grantedAt
) {
}