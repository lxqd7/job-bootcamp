package com.jobbootcamp.course.controller;

import com.jobbootcamp.common.web.ApiResponse;
import com.jobbootcamp.course.application.RouteService;
import com.jobbootcamp.course.domain.dto.PageResponse;
import com.jobbootcamp.course.domain.dto.RouteDetail;
import com.jobbootcamp.course.domain.dto.RouteSummary;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/routes")
public class RouteController {

    private final RouteService routeService;

    public RouteController(RouteService routeService) {
        this.routeService = routeService;
    }

    @GetMapping
    public ApiResponse<PageResponse<RouteSummary>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResponse.ok(routeService.publicList(page, pageSize));
    }

    @GetMapping("/{id}")
    public ApiResponse<RouteDetail> detail(@PathVariable("id") Long id) {
        return ApiResponse.ok(routeService.publicDetail(id));
    }
}