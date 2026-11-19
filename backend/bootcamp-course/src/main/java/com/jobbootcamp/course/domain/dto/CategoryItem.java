package com.jobbootcamp.course.domain.dto;

public record CategoryItem(
        String id,
        String name,
        String parentId,
        int sort,
        boolean enabled,
        long revision
) {
}