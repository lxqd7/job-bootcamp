package com.jobbootcamp.marketing.domain.dto;

import java.util.List;

public record BoardResponse(
        String month,
        List<BoardItemView> items,
        long total,
        int page,
        int pageSize,
        Integer myRank
) {
}