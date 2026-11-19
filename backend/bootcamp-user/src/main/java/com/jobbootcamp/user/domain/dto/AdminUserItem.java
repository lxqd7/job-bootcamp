package com.jobbootcamp.user.domain.dto;

public record AdminUserItem(
        String id,
        String username,
        String nickname,
        String role,
        boolean enabled,
        long revision,
        String createdAt
) {
}