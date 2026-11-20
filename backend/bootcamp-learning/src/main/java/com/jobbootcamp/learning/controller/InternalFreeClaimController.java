package com.jobbootcamp.learning.controller;

import com.jobbootcamp.contracts.internal.FreeClaimCommand;
import com.jobbootcamp.contracts.internal.FreeClaimResult;
import com.jobbootcamp.learning.application.EntitlementService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/int/v1/free-claims")
public class InternalFreeClaimController {

    private final EntitlementService entitlementService;

    public InternalFreeClaimController(EntitlementService entitlementService) {
        this.entitlementService = entitlementService;
    }

    @PostMapping
    public FreeClaimResult grant(@RequestBody FreeClaimCommand command) {
        return entitlementService.grantFreeClaim(command);
    }
}