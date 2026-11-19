package com.jobbootcamp.user.controller;

import com.jobbootcamp.common.web.ApiResponse;
import com.jobbootcamp.user.application.AuthService;
import com.jobbootcamp.user.application.ProfileService;
import com.jobbootcamp.user.domain.dto.ChangePasswordRequest;
import com.jobbootcamp.user.domain.dto.MeResponse;
import com.jobbootcamp.user.domain.dto.UpdateProfileRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me")
public class ProfileController {

    private final ProfileService profileService;
    private final AuthService authService;

    public ProfileController(ProfileService profileService, AuthService authService) {
        this.profileService = profileService;
        this.authService = authService;
    }

    @GetMapping
    public ApiResponse<MeResponse> me(@RequestAttribute("currentUserId") Long userId) {
        return ApiResponse.ok(profileService.profile(userId));
    }

    @PatchMapping
    public ApiResponse<MeResponse> update(@RequestAttribute("currentUserId") Long userId,
                                          @Valid @RequestBody UpdateProfileRequest request) {
        return ApiResponse.ok(profileService.updateProfile(userId, request));
    }

    @PutMapping("/password")
    public ApiResponse<Void> changePassword(@RequestAttribute("currentUserId") Long userId,
                                            @Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(userId, request);
        return ApiResponse.ok(null);
    }
}