package com.jobbootcamp.trade.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class UserRefundScheduler {

    private static final Logger log = LoggerFactory.getLogger(UserRefundScheduler.class);

    private final UserRefundService userRefundService;

    public UserRefundScheduler(UserRefundService userRefundService) {
        this.userRefundService = userRefundService;
    }

    @Scheduled(fixedDelayString = "${app.refund.poll-ms:30000}")
    public void poll() {
        try {
            int handled = userRefundService.retryPending(20);
            if (handled > 0) {
                log.info("user refund retry handled={}", handled);
            }
        } catch (RuntimeException e) {
            log.warn("user refund retry failed: {}", e.getMessage());
        }
    }
}