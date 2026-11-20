package com.jobbootcamp.marketing.messaging;

import com.jobbootcamp.contracts.event.EventPayloads;
import com.jobbootcamp.marketing.application.LessonCompletionService;
import com.jobbootcamp.marketing.application.PointsService;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

@Component
@ConditionalOnProperty(name = "app.outbox.transport", havingValue = "rabbit")
public class LessonCompletedListener {

    private final LessonCompletionService completionService;

    public LessonCompletedListener(LessonCompletionService completionService) {
        this.completionService = completionService;
    }

    @RabbitListener(queues = "bc.marketing.learning-lesson.v1")
    public void onLessonCompleted(Message message) {
        String payload = new String(message.getBody(), StandardCharsets.UTF_8);
        String userId = EventPayloads.stringField(payload, "userId");
        String lessonId = EventPayloads.stringField(payload, "lessonId");
        String occurredAt = EventPayloads.stringField(payload, "occurredAt");
        String sequence = EventPayloads.stringField(payload, "completionEventSequence");
        if (userId == null || lessonId == null || occurredAt == null || sequence == null) {
            throw new IllegalArgumentException("lesson completion payload missing required fields");
        }
        completionService.handle(Long.valueOf(userId), Long.valueOf(lessonId),
                Long.valueOf(sequence), Instant.parse(occurredAt));
    }
}