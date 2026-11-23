package com.jobbootcamp.assignment.messaging;

public record AiTaskQueuedEvent(Long taskId, Long submissionId, Long userId) {
}