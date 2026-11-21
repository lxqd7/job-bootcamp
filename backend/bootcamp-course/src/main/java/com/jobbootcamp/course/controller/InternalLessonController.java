package com.jobbootcamp.course.controller;

import com.jobbootcamp.contracts.internal.CourseCatalogRequest;
import com.jobbootcamp.contracts.internal.CourseCatalogResponse;
import com.jobbootcamp.contracts.internal.LessonMetadataRequest;
import com.jobbootcamp.contracts.internal.LessonMetadataResponse;
import com.jobbootcamp.contracts.internal.PlayAuthorizationResponse;
import com.jobbootcamp.contracts.internal.PlaySessionCheckRequest;
import com.jobbootcamp.contracts.internal.PlaySessionCheckResponse;
import com.jobbootcamp.contracts.internal.PlaySessionIssueRequest;
import com.jobbootcamp.contracts.internal.ProductSnapshotRequest;
import com.jobbootcamp.contracts.internal.ProductSnapshotResponse;
import com.jobbootcamp.contracts.internal.RouteSnapshotRequest;
import com.jobbootcamp.contracts.internal.RouteSnapshotResponse;
import com.jobbootcamp.course.application.LessonPlayService;
import com.jobbootcamp.course.application.ProductSnapshotService;
import com.jobbootcamp.course.application.RouteProductSnapshotService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/int/v1")
public class InternalLessonController {

    private final LessonPlayService lessonPlayService;
    private final ProductSnapshotService productSnapshotService;
    private final RouteProductSnapshotService routeProductSnapshotService;

    public InternalLessonController(LessonPlayService lessonPlayService,
                                    ProductSnapshotService productSnapshotService,
                                    RouteProductSnapshotService routeProductSnapshotService) {
        this.lessonPlayService = lessonPlayService;
        this.productSnapshotService = productSnapshotService;
        this.routeProductSnapshotService = routeProductSnapshotService;
    }

    @PostMapping("/lessons/metadata")
    public LessonMetadataResponse metadata(@RequestBody LessonMetadataRequest request) {
        return lessonPlayService.metadata(request.lessonId());
    }

    @PostMapping("/courses/catalog")
    public CourseCatalogResponse catalog(@RequestBody CourseCatalogRequest request) {
        return lessonPlayService.catalog(request.courseId());
    }

    @PostMapping("/products/snapshots")
    public ProductSnapshotResponse snapshot(@RequestBody ProductSnapshotRequest request) {
        return productSnapshotService.courseSnapshot(request.productId());
    }

    @PostMapping("/routes/snapshots")
    public RouteSnapshotResponse routeSnapshot(@RequestBody RouteSnapshotRequest request) {
        return routeProductSnapshotService.routeSnapshot(request.routeId());
    }

    @PostMapping("/play-sessions/issue")
    public PlayAuthorizationResponse issue(@RequestBody PlaySessionIssueRequest request) {
        return lessonPlayService.issue(request.userId(), request.authSessionId(), request.lessonId());
    }

    @PostMapping("/play-sessions/check")
    public PlaySessionCheckResponse check(@RequestBody PlaySessionCheckRequest request) {
        return lessonPlayService.check(request.userId(), request.playSessionId(), request.lessonId());
    }
}