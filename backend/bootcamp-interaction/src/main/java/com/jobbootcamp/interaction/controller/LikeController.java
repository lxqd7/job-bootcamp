package com.jobbootcamp.interaction.controller;

import com.jobbootcamp.common.web.ApiResponse;
import com.jobbootcamp.interaction.application.LikeService;
import com.jobbootcamp.interaction.config.InteractionAuthInterceptor;
import com.jobbootcamp.interaction.domain.dto.LikeResult;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/likes")
public class LikeController {

    private final LikeService service;

    public LikeController(LikeService service) {
        this.service = service;
    }

    @PutMapping("/{type}/{id}")
    public ApiResponse<LikeResult> like(
            @PathVariable("type") String type,
            @PathVariable("id") Long id,
            @RequestAttribute(InteractionAuthInterceptor.USER_ID_ATTRIBUTE) Long userId,
            @RequestAttribute(InteractionAuthInterceptor.ROLE_ATTRIBUTE) String role) {
        return ApiResponse.ok(service.like(userId, role, type, id));
    }

    @DeleteMapping("/{type}/{id}")
    public ApiResponse<LikeResult> unlike(
            @PathVariable("type") String type,
            @PathVariable("id") Long id,
            @RequestAttribute(InteractionAuthInterceptor.USER_ID_ATTRIBUTE) Long userId,
            @RequestAttribute(InteractionAuthInterceptor.ROLE_ATTRIBUTE) String role) {
        return ApiResponse.ok(service.unlike(userId, role, type, id));
    }
}