package com.jobbootcamp.marketing.messaging;

import com.jobbootcamp.contracts.event.EventEnvelope;
import com.jobbootcamp.contracts.event.EventPayloads;
import com.jobbootcamp.marketing.application.InboxProcessor;
import com.jobbootcamp.marketing.application.PointsService;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

/**
 * 优秀奖励撤回消费。撤回先于奖励到达时，PointsService 会先保存 pending，
 * 等奖励事件处理时在同一账户锁下完成净额 0 的撤回。
 */
@Component
@ConditionalOnProperty(name = "app.outbox.transport", havingValue = "rabbit")
public class RewardRevokedListener {

    private static final String CONSUMER = "interaction-reward-revoked-v1";
    private static final String EVENT_TYPE = "interaction.reward-revoked.v1";

    private final InboxProcessor inboxProcessor;
    private final PointsService pointsService;

    public RewardRevokedListener(InboxProcessor inboxProcessor, PointsService pointsService) {
        this.inboxProcessor = inboxProcessor;
        this.pointsService = pointsService;
    }

    @RabbitListener(queues = "bc.marketing.interaction-reward-revoked.v1")
    public void onRewardRevoked(Message message) {
        String payload = new String(message.getBody(), StandardCharsets.UTF_8);
        String eventId = message.getMessageProperties().getMessageId();
        String noteId = EventPayloads.stringField(payload, "noteId");
        String originalEventId = EventPayloads.stringField(payload, "originalRewardEventId");
        String reason = EventPayloads.stringField(payload, "reason");
        String occurredAt = EventPayloads.stringField(payload, "occurredAt");
        if (eventId == null || noteId == null || originalEventId == null || occurredAt == null) {
            throw new IllegalArgumentException("reward revoked payload missing required fields");
        }
        EventEnvelope event = new EventEnvelope(eventId, EVENT_TYPE, 1, noteId, 1,
                Instant.parse(occurredAt), null, payload);
        inboxProcessor.process(CONSUMER, event, "REVOKE:" + noteId, ignored ->
                pointsService.revokeExcellent(Long.parseLong(noteId), originalEventId,
                        reason == null ? "" : reason, Instant.parse(occurredAt)));
    }
}