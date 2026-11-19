package com.jobbootcamp.course.domain.dto;

import jakarta.validation.constraints.NotNull;

public record PublishRequest(@NotNull Long revision) {
}