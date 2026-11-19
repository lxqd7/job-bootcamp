package com.jobbootcamp.course.domain.dto;

import java.util.List;

public record CategoryTreeNode(
        String id,
        String name,
        String parentId,
        int sort,
        boolean enabled,
        long revision,
        List<CategoryTreeNode> children
) {
}