package com.jobbootcamp.interaction.domain.dto;

public record QuestionRequest(Long lessonId, String title, String body, Boolean anonymous, Long revision) {
}