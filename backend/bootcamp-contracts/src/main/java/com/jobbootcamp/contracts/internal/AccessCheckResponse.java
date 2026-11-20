package com.jobbootcamp.contracts.internal;

public record AccessCheckResponse(boolean allowed, String sourceId) {
}