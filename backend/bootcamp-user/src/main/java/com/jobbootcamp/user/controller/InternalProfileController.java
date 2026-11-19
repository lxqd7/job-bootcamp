package com.jobbootcamp.user.controller;

import com.jobbootcamp.contracts.internal.ProfileBatchItem;
import com.jobbootcamp.contracts.internal.ProfileBatchRequest;
import com.jobbootcamp.contracts.internal.ProfileBatchResponse;
import com.jobbootcamp.user.application.ProfileService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/int/v1/profiles")
public class InternalProfileController {

    private final ProfileService profileService;

    public InternalProfileController(ProfileService profileService) {
        this.profileService = profileService;
    }

    @PostMapping("/batch")
    public ProfileBatchResponse batch(@RequestBody ProfileBatchRequest request) {
        return new ProfileBatchResponse(profileService.batchProfiles(request.userIds()));
    }
}