package com.jobbootcamp.course.messaging;

import com.jobbootcamp.course.infrastructure.OutboxEventMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/dev/outbox")
@ConditionalOnProperty(name = "app.outbox.transport", havingValue = "rabbit")
public class DevOutboxController {

    private final OutboxRelay relay;

    public DevOutboxController(OutboxRelay relay) {
        this.relay = relay;
    }

    @PostMapping("/relay")
    public OutboxRelay.RelayResult relay(@RequestParam(name = "limit", defaultValue = "20") int limit) {
        return relay.relayOnce(limit);
    }
}