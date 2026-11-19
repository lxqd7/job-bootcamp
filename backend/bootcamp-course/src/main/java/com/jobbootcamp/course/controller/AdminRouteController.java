package com.jobbootcamp.course.controller;

import com.jobbootcamp.common.web.ApiResponse;
import com.jobbootcamp.course.application.RouteService;
import com.jobbootcamp.course.domain.dto.PageResponse;
import com.jobbootcamp.course.domain.dto.RouteCreateRequest;
import com.jobbootcamp.course.domain.dto.RouteDetail;
import com.jobbootcamp.course.domain.dto.RoutePublishRequest;
import com.jobbootcamp.course.domain.dto.RouteSummary;
import com.jobbootcamp.course.domain.dto.RouteUpdateRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/routes")
public class AdminRouteController {

    private final RouteService routeService;

    public AdminRouteController(RouteService routeService) {
        this.routeService = routeService;
    }

    @GetMapping
    public ApiResponse<PageResponse<RouteSummary>> list(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResponse.ok(routeService.adminList(status, page, pageSize));
    }

    @GetMapping("/{id}")
    public ApiResponse<RouteDetail> detail(@PathVariable("id") Long id) {
        return ApiResponse.ok(routeService.adminDetail(id));
    }

    @PostMapping
    public ApiResponse<RouteSummary> create(@Valid @RequestBody RouteCreateRequest request) {
        return ApiResponse.ok(routeService.create(request));
    }

    @PatchMapping("/{id}")
    public ApiResponse<RouteSummary> update(@PathVariable("id") Long id,
                                            @Valid @RequestBody RouteUpdateRequest request) {
        return ApiResponse.ok(routeService.update(id, request));
    }

    @PostMapping("/{id}/publish")
    public ApiResponse<RouteSummary> publish(@PathVariable("id") Long id,
                                             @Valid @RequestBody RoutePublishRequest request) {
        return ApiResponse.ok(routeService.publish(id, request.revision()));
    }

    @PostMapping("/{id}/offline")
    public ApiResponse<RouteSummary> offline(@PathVariable("id") Long id,
                                             @Valid @RequestBody RoutePublishRequest request) {
        return ApiResponse.ok(routeService.offline(id, request.revision()));
    }
}