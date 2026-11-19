package com.jobbootcamp.marketing.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.outbox.relay-enabled", havingValue = "true")
public class OutboxScheduler {

    private static final Logger log = LoggerFactory.getLogger(OutboxScheduler.class);

    private final OutboxRelay relay;

    public OutboxScheduler(OutboxRelay relay) {
        this.relay = relay;
    }

    @Scheduled(fixedDelayString = "${app.outbox.poll-interval-ms:2000}")
    public void poll() {
        OutboxRelay.RelayResult result = relay.relayOnce();
        if (result.claimed() > 0) {
            log.info("outbox relay claimed={} sent={} failed={}",
                    result.claimed(), result.sent(), result.failed());
        }
    }
}