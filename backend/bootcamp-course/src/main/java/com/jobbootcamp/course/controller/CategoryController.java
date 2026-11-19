package com.jobbootcamp.course.controller;

import com.jobbootcamp.common.web.ApiResponse;
import com.jobbootcamp.course.application.CategoryNode;
import com.jobbootcamp.course.application.CategoryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/categories")
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @GetMapping
    public ApiResponse<List<CategoryNode>> list() {
        return ApiResponse.ok(categoryService.tree());
    }
}