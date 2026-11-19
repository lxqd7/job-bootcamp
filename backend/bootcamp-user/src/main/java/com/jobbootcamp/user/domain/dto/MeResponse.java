package com.jobbootcamp.user.domain.dto;

import java.util.List;

public record MeResponse(
        String id,
        String nickname,
        String role,
        String avatarUrl,
        List<String> permissions,
        long revision
) {
}