package com.jobbootcamp.user.controller;

import com.jobbootcamp.common.web.ApiResponse;
import com.jobbootcamp.user.application.AdminUserService;
import com.jobbootcamp.user.domain.RoleCode;
import com.jobbootcamp.user.domain.dto.AdminUserItem;
import com.jobbootcamp.user.domain.dto.CreateAdminUserRequest;
import com.jobbootcamp.user.domain.dto.PageResponse;
import com.jobbootcamp.user.domain.dto.UpdateAdminUserRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/users")
public class AdminUserController {

    private final AdminUserService adminUserService;

    public AdminUserController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    @GetMapping
    public ApiResponse<PageResponse<AdminUserItem>> list(
            @RequestAttribute("currentUserId") Long currentUserId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) RoleCode role,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResponse.ok(adminUserService.list(currentUserId, keyword, role, page, pageSize));
    }

    @PostMapping
    public ApiResponse<AdminUserItem> create(
            @RequestAttribute("currentUserId") Long currentUserId,
            @Valid @RequestBody CreateAdminUserRequest request) {
        return ApiResponse.ok(adminUserService.create(currentUserId, request));
    }

    @PatchMapping("/{id}")
    public ApiResponse<AdminUserItem> update(
            @RequestAttribute("currentUserId") Long currentUserId,
            @PathVariable("id") Long id,
            @Valid @RequestBody UpdateAdminUserRequest request) {
        return ApiResponse.ok(adminUserService.update(currentUserId, id, request));
    }
}