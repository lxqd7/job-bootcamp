package com.jobbootcamp.trade.domain.dto;

import java.util.List;

public record PageResponse<T>(List<T> items, long total, long page, long pageSize) {
}