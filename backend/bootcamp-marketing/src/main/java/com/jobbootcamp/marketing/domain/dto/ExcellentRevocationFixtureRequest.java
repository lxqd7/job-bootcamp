package com.jobbootcamp.marketing.domain.dto;

import java.time.Instant;

public record ExcellentRevocationFixtureRequest(
        Long noteId,
        String originalEventId,
        Instant occurredAt,
        String reason
) {
}