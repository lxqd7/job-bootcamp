package com.jobbootcamp.course.controller;

import com.jobbootcamp.common.web.ApiResponse;
import com.jobbootcamp.course.application.CategoryService;
import com.jobbootcamp.course.domain.dto.CategoryCreateRequest;
import com.jobbootcamp.course.domain.dto.CategoryItem;
import com.jobbootcamp.course.domain.dto.CategoryTreeNode;
import com.jobbootcamp.course.domain.dto.CategoryUpdateRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/categories")
public class AdminCategoryController {

    private final CategoryService categoryService;

    public AdminCategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @GetMapping
    public ApiResponse<List<CategoryTreeNode>> tree() {
        return ApiResponse.ok(categoryService.adminTree());
    }

    @PostMapping
    public ApiResponse<CategoryItem> create(@Valid @RequestBody CategoryCreateRequest request) {
        return ApiResponse.ok(categoryService.create(request));
    }

    @PatchMapping("/{id}")
    public ApiResponse<CategoryItem> update(@PathVariable("id") Long id,
                                            @Valid @RequestBody CategoryUpdateRequest request) {
        return ApiResponse.ok(categoryService.update(id, request));
    }
}