package com.jobbootcamp.marketing.application;

import com.jobbootcamp.contracts.internal.CompletionEventItem;
import com.jobbootcamp.marketing.infrastructure.LearningEventCursorMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class LessonCompletionService {

    private static final Logger log = LoggerFactory.getLogger(LessonCompletionService.class);

    private final LearningCompletionClient completionClient;
    private final LearningEventCursorMapper cursorMapper;
    private final PointsService pointsService;

    public LessonCompletionService(LearningCompletionClient completionClient,
                                   LearningEventCursorMapper cursorMapper,
                                   PointsService pointsService) {
        this.completionClient = completionClient;
        this.cursorMapper = cursorMapper;
        this.pointsService = pointsService;
    }

    public void handle(long userId, long lessonId, long sequence, Instant occurredAt) {
        long cursor = currentCursor(userId);
        if (sequence <= cursor) {
            return;
        }
        List<CompletionEventItem> events = new ArrayList<>();
        while (cursor + 1 < sequence) {
            List<CompletionEventItem> batch = completionClient.fetch(userId, cursor, 100);
            if (batch.isEmpty()) {
                break;
            }
            events.addAll(batch);
            cursor = batch.get(batch.size() - 1).completionEventSequence();
        }
        events.add(new CompletionEventItem(String.valueOf(userId), null, String.valueOf(lessonId),
                sequence, occurredAt));
        events.sort(Comparator.comparingLong(CompletionEventItem::completionEventSequence));

        long applied = currentCursor(userId);
        for (CompletionEventItem event : events) {
            if (event.completionEventSequence() <= applied) {
                continue;
            }
            long eventLessonId = event.lessonId() == null ? lessonId : Long.parseLong(event.lessonId());
            pointsService.awardVideo(userId, eventLessonId, 10, event.occurredAt());
            cursorMapper.upsert(userId, event.completionEventSequence(), LocalDateTime.now(ZoneOffset.UTC));
            applied = event.completionEventSequence();
            log.info("lesson completion applied user={} lesson={} sequence={}", userId, eventLessonId,
                    event.completionEventSequence());
        }
    }

    private long currentCursor(long userId) {
        Long value = cursorMapper.selectLastSequence(userId);
        return value == null ? 0 : value;
    }
}