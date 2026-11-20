package com.jobbootcamp.learning.controller;

import com.jobbootcamp.learning.application.EntitlementService;
import com.jobbootcamp.learning.domain.dto.AccessCheckRequest;
import com.jobbootcamp.learning.domain.dto.AccessCheckResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/int/v1/access")
public class InternalAccessController {

    private final EntitlementService entitlementService;

    public InternalAccessController(EntitlementService entitlementService) {
        this.entitlementService = entitlementService;
    }

    @PostMapping("/check")
    public AccessCheckResponse check(@Valid @RequestBody AccessCheckRequest request) {
        return entitlementService.checkAccess(request.userId(), request.courseId());
    }
}