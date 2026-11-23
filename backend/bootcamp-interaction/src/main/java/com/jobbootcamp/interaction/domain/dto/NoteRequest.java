package com.jobbootcamp.interaction.domain.dto;

public record NoteRequest(Long lessonId, Long positionMs, String body, String visibility, Long revision) {
}