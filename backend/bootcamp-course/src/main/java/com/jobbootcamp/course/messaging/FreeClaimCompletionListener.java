package com.jobbootcamp.course.messaging;

import com.jobbootcamp.contracts.event.EventPayloads;
import com.jobbootcamp.course.application.FreeClaimService;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Component
@ConditionalOnProperty(name = "app.outbox.transport", havingValue = "rabbit")
public class FreeClaimCompletionListener {

    private final FreeClaimService freeClaimService;

    public FreeClaimCompletionListener(FreeClaimService freeClaimService) {
        this.freeClaimService = freeClaimService;
    }

    @RabbitListener(queues = RabbitTopologyConfig.COMPLETION_QUEUE)
    public void onCompletion(Message message) {
        String payload = new String(message.getBody(), StandardCharsets.UTF_8);
        String claimId = EventPayloads.stringField(payload, "claimId");
        String sourceId = EventPayloads.stringField(payload, "sourceId");
        if (claimId == null || sourceId == null) {
            throw new IllegalArgumentException("free claim completion payload missing claimId/sourceId");
        }
        freeClaimService.markFulfilled(claimId, sourceId);
    }
}