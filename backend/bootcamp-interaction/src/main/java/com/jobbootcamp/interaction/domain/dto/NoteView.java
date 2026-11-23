package com.jobbootcamp.interaction.domain.dto;

import java.time.LocalDateTime;

public record NoteView(
        Long id,
        Long courseId,
        Long lessonId,
        Long positionMs,
        String type,
        String visibility,
        String state,
        String body,
        Long authorId,
        String authorDisplay,
        Long sourceNoteId,
        Object sourceAuthorSnapshot,
        long revision,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        boolean mine
) {
}