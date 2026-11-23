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
 * 优秀笔记奖励消费：inbox 保证事件只处理一次，积分账本的 (EXCELLENT,noteId)
 * 唯一键再做业务兜底，重复投递或重评都不会重复发分。
 */
@Component
@ConditionalOnProperty(name = "app.outbox.transport", havingValue = "rabbit")
public class NoteExcellentListener {

    private static final String CONSUMER = "interaction-note-excellent-v1";
    private static final String EVENT_TYPE = "interaction.note-excellent.v1";

    private final InboxProcessor inboxProcessor;
    private final PointsService pointsService;

    public NoteExcellentListener(InboxProcessor inboxProcessor, PointsService pointsService) {
        this.inboxProcessor = inboxProcessor;
        this.pointsService = pointsService;
    }

    @RabbitListener(queues = "bc.marketing.interaction-note-excellent.v1")
    public void onNoteExcellent(Message message) {
        String payload = new String(message.getBody(), StandardCharsets.UTF_8);
        String eventId = message.getMessageProperties().getMessageId();
        String noteId = EventPayloads.stringField(payload, "noteId");
        String authorId = EventPayloads.stringField(payload, "authorId");
        String occurredAt = EventPayloads.stringField(payload, "occurredAt");
        if (eventId == null || noteId == null || authorId == null || occurredAt == null) {
            throw new IllegalArgumentException("note excellent payload missing required fields");
        }
        EventEnvelope event = new EventEnvelope(eventId, EVENT_TYPE, 1, noteId, 1,
                Instant.parse(occurredAt), null, payload);
        inboxProcessor.process(CONSUMER, event, "EXCELLENT:" + noteId, ignored ->
                pointsService.awardExcellent(Long.parseLong(authorId), Long.parseLong(noteId),
                        Instant.parse(occurredAt)));
    }
}