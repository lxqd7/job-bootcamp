package com.jobbootcamp.course.domain.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record RouteUpdateRequest(
        @NotBlank @Size(max = 200) String title,
        @Size(max = 20000) String description,
        @NotNull @Min(1) Long priceCent,
        @NotEmpty List<RouteMemberRequest> members,
        @NotNull Long revision
) {
}