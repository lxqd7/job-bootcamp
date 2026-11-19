package com.jobbootcamp.contracts.internal;

import java.util.List;

public record SessionCheckResponse(
        boolean active,
        String userId,
        String role,
        String sessionId,
        List<String> permissions
) {
    public static SessionCheckResponse inactive() {
        return new SessionCheckResponse(false, null, null, null, List.of());
    }
}