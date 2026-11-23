package com.jobbootcamp.assignment.domain.dto;

public record PageResponse<T>(java.util.List<T> items, long total, long page, long pageSize) {
}