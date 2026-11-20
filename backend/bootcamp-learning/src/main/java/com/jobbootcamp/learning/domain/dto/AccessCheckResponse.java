package com.jobbootcamp.learning.domain.dto;

public record AccessCheckResponse(boolean allowed, String sourceId) {
}