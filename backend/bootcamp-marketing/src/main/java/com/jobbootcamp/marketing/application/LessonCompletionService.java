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

/**
 * 消费学习首次完成事件。按 completionEventSequence 连续推进，缺号先向学习服务补洞；
 * 补洞返回空或仍有缺口时抛异常且不推进游标，避免后续积分被静默跳过。
 */
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
        if (sequence > cursor + 1) {
            List<CompletionEventItem> backfill = completionClient.fetch(userId, cursor, 100);
            cursor = applyContiguous(userId, lessonId, cursor, backfill);
            if (cursor + 1 < sequence) {
                throw new IllegalStateException("completion event gap not filled: cursor=" + cursor
                        + " incoming=" + sequence);
            }
        }
        if (cursor < sequence) {
            applyOne(userId, lessonId, sequence, occurredAt);
        }
    }

    private long applyContiguous(long userId, long fallbackLessonId, long cursor,
                                 List<CompletionEventItem> events) {
        List<CompletionEventItem> ordered = new ArrayList<>(events);
        ordered.sort(Comparator.comparingLong(CompletionEventItem::completionEventSequence));
        long next = cursor + 1;
        for (CompletionEventItem event : ordered) {
            if (event.completionEventSequence() < next) {
                continue;
            }
            if (event.completionEventSequence() > next) {
                break;
            }
            long lessonId = event.lessonId() == null
                    ? fallbackLessonId : Long.parseLong(event.lessonId());
            applyOne(userId, lessonId, event.completionEventSequence(), event.occurredAt());
            cursor = event.completionEventSequence();
            next++;
        }
        return cursor;
    }

    private void applyOne(long userId, long lessonId, long sequence, Instant occurredAt) {
        pointsService.awardVideo(userId, lessonId, 10, occurredAt);
        cursorMapper.upsert(userId, sequence, LocalDateTime.now(ZoneOffset.UTC));
        log.info("lesson completion applied user={} lesson={} sequence={}", userId, lessonId, sequence);
    }

    private long currentCursor(long userId) {
        Long value = cursorMapper.selectLastSequence(userId);
        return value == null ? 0 : value;
    }
}