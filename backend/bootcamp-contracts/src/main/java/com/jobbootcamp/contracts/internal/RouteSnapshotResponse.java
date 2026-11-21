package com.jobbootcamp.contracts.internal;

import java.util.List;

/**
 * 交易报价使用的路线不可变商品快照；member 的 allocatedCent 已按路线售价分摊。
 */
public record RouteSnapshotResponse(
        Long routeId,
        String title,
        String description,
        Long versionNo,
        Long priceCent,
        String status,
        List<Member> members
) {
    public record Member(
            Long courseId,
            String title,
            Long courseVersion,
            Long epochId,
            Long unitPriceCent,
            Long allocatedCent,
            boolean free,
            Long categoryId,
            List<Long> categoryAncestorIds,
            List<ProductSnapshotResponse.SnapshotLesson> lessons
    ) {
    }
}