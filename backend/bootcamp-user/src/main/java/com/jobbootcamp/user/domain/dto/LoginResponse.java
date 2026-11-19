package com.jobbootcamp.user.domain.dto;

public record LoginResponse(
        String accessToken,
        long expiresIn,
        UserSummary user,
        String csrfToken
) {
}