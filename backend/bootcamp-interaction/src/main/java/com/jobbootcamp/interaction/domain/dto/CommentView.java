package com.jobbootcamp.interaction.domain.dto;

import java.time.LocalDateTime;

public record CommentView(
        Long id,
        Long answerId,
        String body,
        String authorDisplay,
        String authorRole,
        boolean mine,
        int likeCount,
        boolean liked,
        long revision,
        LocalDateTime createdAt
) {
}