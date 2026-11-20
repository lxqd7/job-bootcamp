package com.jobbootcamp.contracts.internal;

public record PlaySessionIssueRequest(Long userId, Long authSessionId, Long lessonId) {
}