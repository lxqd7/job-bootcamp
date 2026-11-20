package com.jobbootcamp.marketing.application;

import com.jobbootcamp.contracts.internal.CompletionEventItem;
import com.jobbootcamp.marketing.infrastructure.LearningEventCursorMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LessonCompletionServiceTest {

    @Test
    void gapIsBackfilledInSequenceOrder() {
        LearningCompletionClient client = mock(LearningCompletionClient.class);
        LearningEventCursorMapper cursor = mock(LearningEventCursorMapper.class);
        PointsService points = mock(PointsService.class);
        LessonCompletionService service = new LessonCompletionService(client, cursor, points);

        Instant occurredAt = Instant.parse("2026-11-20T00:00:00Z");
        when(cursor.selectLastSequence(1L)).thenReturn(0L);
        when(client.fetch(1L, 0L, 100)).thenReturn(List.of(
                new CompletionEventItem("1", "9", "101", 1, occurredAt),
                new CompletionEventItem("1", "9", "102", 2, occurredAt)));

        service.handle(1L, 103L, 3L, occurredAt);

        verify(points).awardVideo(1L, 101L, 10, occurredAt);
        verify(points).awardVideo(1L, 102L, 10, occurredAt);
        verify(points).awardVideo(1L, 103L, 10, occurredAt);
        verify(cursor, times(3)).upsert(eq(1L), anyLong(), any());
    }

    @Test
    void inOrderEventDoesNotFetchBackfill() {
        LearningCompletionClient client = mock(LearningCompletionClient.class);
        LearningEventCursorMapper cursor = mock(LearningEventCursorMapper.class);
        PointsService points = mock(PointsService.class);
        LessonCompletionService service = new LessonCompletionService(client, cursor, points);

        Instant occurredAt = Instant.parse("2026-11-20T00:00:00Z");
        when(cursor.selectLastSequence(1L)).thenReturn(0L);

        service.handle(1L, 101L, 1L, occurredAt);

        verify(client, never()).fetch(anyLong(), anyLong(), anyInt());
        verify(points).awardVideo(1L, 101L, 10, occurredAt);
        verify(cursor).upsert(eq(1L), eq(1L), any());
    }
}