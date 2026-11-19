package com.jobbootcamp.contracts.internal;

public record ProfileBatchItem(
        String id,
        String nickname,
        String role,
        boolean enabled,
        long revision
) {
}