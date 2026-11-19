package com.jobbootcamp.course.application;

import com.jobbootcamp.course.domain.Category;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class CategoryTreeBuilder {

    private static final long ROOT_PARENT_ID = 0L;

    private CategoryTreeBuilder() {
    }

    public static List<CategoryNode> build(List<Category> categories) {
        Map<Long, List<Category>> childrenByParent = new LinkedHashMap<>();
        for (Category category : categories) {
            childrenByParent.computeIfAbsent(category.getParentId(), parentId -> new ArrayList<>())
                    .add(category);
        }
        return buildChildren(ROOT_PARENT_ID, childrenByParent);
    }

    private static List<CategoryNode> buildChildren(Long parentId, Map<Long, List<Category>> childrenByParent) {
        return childrenByParent.getOrDefault(parentId, List.of()).stream()
                .map(category -> new CategoryNode(
                        String.valueOf(category.getId()),
                        category.getName(),
                        buildChildren(category.getId(), childrenByParent)))
                .toList();
    }
}