package com.jobbootcamp.learning.controller;

import com.jobbootcamp.learning.application.EntitlementService;
import com.jobbootcamp.contracts.internal.AccessCheckRequest;
import com.jobbootcamp.contracts.internal.AccessCheckResponse;
import com.jobbootcamp.contracts.internal.BenefitAccessCheckRequest;
import com.jobbootcamp.contracts.internal.BenefitAccessCheckResponse;
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

    @PostMapping("/benefit")
    public BenefitAccessCheckResponse benefit(@RequestBody BenefitAccessCheckRequest request) {
        return entitlementService.checkBenefitAccess(
                request.userId(), request.courseId(), request.routes());
    }
}