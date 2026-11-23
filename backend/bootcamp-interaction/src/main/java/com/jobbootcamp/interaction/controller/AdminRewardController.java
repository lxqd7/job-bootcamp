package com.jobbootcamp.interaction.controller;

import com.jobbootcamp.common.web.ApiResponse;
import com.jobbootcamp.interaction.application.NoteService;
import com.jobbootcamp.interaction.config.ModerationInterceptor;
import com.jobbootcamp.interaction.domain.dto.RevokeRewardRequest;
import com.jobbootcamp.interaction.domain.dto.RevokeRewardView;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/notes")
public class AdminRewardController {

    private final NoteService service;

    public AdminRewardController(NoteService service) {
        this.service = service;
    }

    @PostMapping("/{id}/revoke-reward")
    public ApiResponse<RevokeRewardView> revoke(
            @PathVariable("id") Long id,
            @RequestBody RevokeRewardRequest request,
            @RequestAttribute(ModerationInterceptor.ADMIN_ID_ATTRIBUTE) Long adminId) {
        return ApiResponse.ok(service.revokeReward(adminId, id, request == null ? null : request.reason()));
    }
}