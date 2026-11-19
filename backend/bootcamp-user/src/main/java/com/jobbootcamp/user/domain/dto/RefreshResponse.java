package com.jobbootcamp.user.domain.dto;

public record RefreshResponse(String accessToken, long expiresIn) {
}