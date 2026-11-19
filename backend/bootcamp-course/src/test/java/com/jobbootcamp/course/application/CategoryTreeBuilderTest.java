package com.jobbootcamp.course.application;

import com.jobbootcamp.course.domain.Category;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CategoryTreeBuilderTest {

    @Test
    void buildsNestedTreeFromFlatCategories() {
        Category javaCategory = category(1L, 0L, "Java");
        Category basics = category(2L, 1L, "基础语法");
        Category advanced = category(3L, 1L, "并发编程");

        List<CategoryNode> tree = CategoryTreeBuilder.build(List.of(javaCategory, basics, advanced));

        assertThat(tree).hasSize(1);
        assertThat(tree.get(0).id()).isEqualTo("1");
        assertThat(tree.get(0).name()).isEqualTo("Java");
        assertThat(tree.get(0).children()).extracting(CategoryNode::name)
                .containsExactly("基础语法", "并发编程");
    }

    @Test
    void returnsEmptyTreeForEmptyInput() {
        assertThat(CategoryTreeBuilder.build(List.of())).isEmpty();
    }

    private Category category(long id, long parentId, String name) {
        Category category = new Category();
        category.setId(id);
        category.setParentId(parentId);
        category.setName(name);
        return category;
    }
}