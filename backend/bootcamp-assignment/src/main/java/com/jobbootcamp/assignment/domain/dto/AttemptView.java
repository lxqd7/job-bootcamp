package com.jobbootcamp.assignment.domain.dto;

import java.time.LocalDateTime;

public record AttemptView(
        Long id,
        Long assignmentId,
        Long versionId,
        String status,
        Object publicBody,
        Object draft,
        Long draftRevision,
        LocalDateTime startedAt
) {
}