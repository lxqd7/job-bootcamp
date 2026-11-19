package com.jobbootcamp.course.controller;

import com.jobbootcamp.common.web.ApiResponse;
import com.jobbootcamp.course.application.MediaService;
import com.jobbootcamp.course.domain.dto.DemoImportRequest;
import com.jobbootcamp.course.domain.dto.MediaImportPreviewRequest;
import com.jobbootcamp.course.domain.dto.MediaImportPreviewResponse;
import com.jobbootcamp.course.domain.dto.MediaImportRequest;
import com.jobbootcamp.course.domain.dto.MediaImportResponse;
import com.jobbootcamp.course.domain.dto.MediaItemView;
import com.jobbootcamp.course.domain.dto.MediaOperationView;
import com.jobbootcamp.course.domain.dto.PageResponse;
import com.jobbootcamp.course.media.MediaAuthorization;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/media")
public class AdminMediaController {

    private final MediaService mediaService;

    public AdminMediaController(MediaService mediaService) {
        this.mediaService = mediaService;
    }

    @GetMapping
    public ApiResponse<PageResponse<MediaItemView>> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String provider,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResponse.ok(mediaService.list(status, provider, page, pageSize));
    }

    @PostMapping("/import-preview")
    public ApiResponse<MediaImportPreviewResponse> preview(@Valid @RequestBody MediaImportPreviewRequest request) {
        return ApiResponse.ok(mediaService.previewImport(request));
    }

    @PostMapping("/import")
    public ApiResponse<MediaImportResponse> importMedia(@Valid @RequestBody MediaImportRequest request) {
        return ApiResponse.ok(mediaService.importMedia(request));
    }

    @GetMapping("/operations/{operationKey}")
    public ApiResponse<MediaOperationView> operation(@PathVariable("operationKey") String operationKey) {
        return ApiResponse.ok(mediaService.operation(operationKey));
    }

    @PostMapping("/{id}/verify")
    public ApiResponse<Map<String, String>> verify(@PathVariable("id") Long id) {
        return ApiResponse.ok(Map.of("operationKey", mediaService.verify(id)));
    }

    @PostMapping("/{id}/preview-authorizations")
    public ApiResponse<MediaAuthorization> previewAuthorization(
            @PathVariable("id") Long id,
            @RequestAttribute("currentUserId") Long currentUserId,
            @RequestAttribute("currentSessionId") Long currentSessionId) {
        return ApiResponse.ok(mediaService.previewAuthorization(id, currentUserId, currentSessionId));
    }

    @PostMapping("/demo-import")
    public ApiResponse<MediaItemView> demoImport(@Valid @RequestBody DemoImportRequest request) {
        return ApiResponse.ok(mediaService.demoImport(request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<MediaItemView> detach(@PathVariable("id") Long id,
                                             @RequestParam("revision") Long revision) {
        return ApiResponse.ok(mediaService.detach(id, revision));
    }
}