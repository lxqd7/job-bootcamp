package com.jobbootcamp.contracts.internal;

public record CompletionEventsRequest(Long userId, Long afterSequence, Integer limit) {
}