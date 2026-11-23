package com.jobbootcamp.interaction.controller;

import com.jobbootcamp.common.web.ApiResponse;
import com.jobbootcamp.interaction.application.AdminGovernanceService;
import com.jobbootcamp.interaction.application.NoteService;
import com.jobbootcamp.interaction.config.ModerationInterceptor;
import com.jobbootcamp.interaction.domain.dto.AdminExcellentRewardView;
import com.jobbootcamp.interaction.domain.dto.AdminModerationAuditView;
import com.jobbootcamp.interaction.domain.dto.PageResponse;
import com.jobbootcamp.interaction.domain.dto.RevokeRewardRequest;
import com.jobbootcamp.interaction.domain.dto.RevokeRewardView;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminRewardController {

    private final NoteService service;
    private final AdminGovernanceService governanceService;

    public AdminRewardController(NoteService service, AdminGovernanceService governanceService) {
        this.service = service;
        this.governanceService = governanceService;
    }

    @GetMapping("/excellent-rewards")
    public ApiResponse<PageResponse<AdminExcellentRewardView>> rewards(
            @RequestParam(required = false) String state,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResponse.ok(governanceService.rewards(state, page, pageSize));
    }

    @GetMapping("/moderation-actions")
    public ApiResponse<PageResponse<AdminModerationAuditView>> audits(
            @RequestParam(required = false) String action,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResponse.ok(governanceService.audits(action, page, pageSize));
    }

    @PostMapping("/notes/{id}/revoke-reward")
    public ApiResponse<RevokeRewardView> revoke(
            @PathVariable("id") Long id,
            @RequestBody RevokeRewardRequest request,
            @RequestAttribute(ModerationInterceptor.ADMIN_ID_ATTRIBUTE) Long adminId) {
        return ApiResponse.ok(service.revokeReward(adminId, id, request == null ? null : request.reason()));
    }
}