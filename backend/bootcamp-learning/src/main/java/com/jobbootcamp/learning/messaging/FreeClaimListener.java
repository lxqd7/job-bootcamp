package com.jobbootcamp.learning.messaging;

import com.jobbootcamp.contracts.event.EventPayloads;
import com.jobbootcamp.contracts.internal.FreeClaimCommand;
import com.jobbootcamp.contracts.internal.FreeClaimResult;
import com.jobbootcamp.learning.application.EntitlementService;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 消费课程免费领取受理事件；授予完成后回传 sourceId，发送失败由 RabbitMQ 重投。
 */
@Component
@ConditionalOnProperty(name = "app.outbox.transport", havingValue = "rabbit")
public class FreeClaimListener {

    private final EntitlementService entitlementService;
    private final RabbitTemplate rabbitTemplate;
    private final String exchange;

    public FreeClaimListener(EntitlementService entitlementService,
                             RabbitTemplate rabbitTemplate,
                             @Value("${app.outbox.exchange:bc.domain.v1}") String exchange) {
        this.entitlementService = entitlementService;
        this.rabbitTemplate = rabbitTemplate;
        this.exchange = exchange;
    }

    @RabbitListener(queues = RabbitTopologyConfig.CLAIM_QUEUE)
    public void onClaim(Message message) throws Exception {
        String payload = new String(message.getBody(), StandardCharsets.UTF_8);
        String claimId = EventPayloads.stringField(payload, "claimId");
        String userId = EventPayloads.stringField(payload, "userId");
        String courseId = EventPayloads.stringField(payload, "courseId");
        String courseVersion = EventPayloads.stringField(payload, "courseVersion");
        String acceptedAt = EventPayloads.stringField(payload, "acceptedAt");
        if (claimId == null || userId == null || courseId == null || courseVersion == null || acceptedAt == null) {
            throw new IllegalArgumentException("free claim payload missing required fields");
        }
        FreeClaimResult result = entitlementService.grantFreeClaim(new FreeClaimCommand(
                claimId, Long.valueOf(userId), Long.valueOf(courseId),
                Long.valueOf(courseVersion), Instant.parse(acceptedAt)));
        publishCompletion(claimId, userId, courseId, result.sourceId());
    }

    private void publishCompletion(String claimId, String userId, String courseId, String sourceId) throws Exception {
        String eventId = UUID.randomUUID().toString();
        String payload = "{\"claimId\":\"" + claimId
                + "\",\"userId\":\"" + userId
                + "\",\"courseId\":\"" + courseId
                + "\",\"sourceId\":\"" + sourceId + "\"}";
        Message message = MessageBuilder.withBody(payload.getBytes(StandardCharsets.UTF_8))
                .setContentType("application/json")
                .setContentEncoding("UTF-8")
                .setMessageId(eventId)
                .setType("learning.free-claim-completed.v1")
                .setDeliveryMode(MessageDeliveryMode.PERSISTENT)
                .build();
        CorrelationData correlation = new CorrelationData(eventId);
        rabbitTemplate.send(exchange, "learning.free-claim-completed.v1", message, correlation);
        CorrelationData.Confirm confirm = correlation.getFuture().get(5, TimeUnit.SECONDS);
        if (confirm == null || !confirm.isAck()) {
            throw new IllegalStateException("completion event was not confirmed by RabbitMQ");
        }
    }
}