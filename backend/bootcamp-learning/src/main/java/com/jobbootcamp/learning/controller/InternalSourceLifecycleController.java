package com.jobbootcamp.learning.controller;

import com.jobbootcamp.contracts.internal.SourceLifecycleRequest;
import com.jobbootcamp.contracts.internal.SourceLifecycleResponse;
import com.jobbootcamp.learning.application.SourceLifecycleService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/int/v1/sources")
public class InternalSourceLifecycleController {

    private final SourceLifecycleService lifecycleService;

    public InternalSourceLifecycleController(SourceLifecycleService lifecycleService) {
        this.lifecycleService = lifecycleService;
    }

    @PostMapping("/revoke")
    public SourceLifecycleResponse revoke(@RequestBody SourceLifecycleRequest request) {
        return lifecycleService.revoke(request);
    }

    @PostMapping("/restore")
    public SourceLifecycleResponse restore(@RequestBody SourceLifecycleRequest request) {
        return lifecycleService.restore(request);
    }
}