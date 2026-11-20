package com.jobbootcamp.contracts.internal;

public record PlaySessionCheckRequest(Long userId, String playSessionId, Long lessonId) {
}