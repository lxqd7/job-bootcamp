package com.jobbootcamp.course.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class FreeClaimRetryScheduler {

    private static final Logger log = LoggerFactory.getLogger(FreeClaimRetryScheduler.class);

    private final FreeClaimService freeClaimService;

    public FreeClaimRetryScheduler(FreeClaimService freeClaimService) {
        this.freeClaimService = freeClaimService;
    }

    @Scheduled(fixedDelayString = "${app.claim.retry-interval-ms:10000}")
    public void retry() {
        try {
            freeClaimService.retryPending();
        } catch (RuntimeException e) {
            log.warn("free claim retry scan failed: {}", e.getMessage());
        }
    }
}