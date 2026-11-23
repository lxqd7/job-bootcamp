package com.jobbootcamp.interaction.controller;

import com.jobbootcamp.common.web.ApiResponse;
import com.jobbootcamp.interaction.application.NoteService;
import com.jobbootcamp.interaction.config.InteractionAuthInterceptor;
import com.jobbootcamp.interaction.domain.dto.NoteRequest;
import com.jobbootcamp.interaction.domain.dto.NoteSummary;
import com.jobbootcamp.interaction.domain.dto.NoteView;
import com.jobbootcamp.interaction.domain.dto.PageResponse;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class NoteController {

    private final NoteService service;

    public NoteController(NoteService service) {
        this.service = service;
    }

    @GetMapping("/courses/{courseId}/notes")
    public ApiResponse<PageResponse<NoteSummary>> courseNotes(
            @PathVariable("courseId") Long courseId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestAttribute(InteractionAuthInterceptor.USER_ID_ATTRIBUTE) Long userId,
            @RequestAttribute(InteractionAuthInterceptor.ROLE_ATTRIBUTE) String role) {
        return ApiResponse.ok(service.courseNotes(userId, role, courseId, page, pageSize));
    }

    @PostMapping("/courses/{courseId}/notes")
    public ApiResponse<NoteView> create(
            @PathVariable("courseId") Long courseId,
            @RequestBody NoteRequest request,
            @RequestAttribute(InteractionAuthInterceptor.USER_ID_ATTRIBUTE) Long userId,
            @RequestAttribute(InteractionAuthInterceptor.ROLE_ATTRIBUTE) String role) {
        return ApiResponse.ok(service.createNote(userId, role, courseId, request));
    }

    @GetMapping("/me/notes")
    public ApiResponse<PageResponse<NoteSummary>> myNotes(
            @RequestParam(required = false) String type,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestAttribute(InteractionAuthInterceptor.USER_ID_ATTRIBUTE) Long userId,
            @RequestAttribute(InteractionAuthInterceptor.ROLE_ATTRIBUTE) String role) {
        return ApiResponse.ok(service.myNotes(userId, role, type, page, pageSize));
    }

    @GetMapping("/notes/{id}")
    public ApiResponse<NoteView> get(
            @PathVariable("id") Long id,
            @RequestAttribute(InteractionAuthInterceptor.USER_ID_ATTRIBUTE) Long userId,
            @RequestAttribute(InteractionAuthInterceptor.ROLE_ATTRIBUTE) String role) {
        return ApiResponse.ok(service.getNote(userId, role, id));
    }

    @PatchMapping("/notes/{id}")
    public ApiResponse<NoteView> update(
            @PathVariable("id") Long id,
            @RequestBody NoteRequest request,
            @RequestAttribute(InteractionAuthInterceptor.USER_ID_ATTRIBUTE) Long userId,
            @RequestAttribute(InteractionAuthInterceptor.ROLE_ATTRIBUTE) String role) {
        return ApiResponse.ok(service.updateNote(userId, role, id, request));
    }

    @DeleteMapping("/notes/{id}")
    public ApiResponse<Void> delete(
            @PathVariable("id") Long id,
            @RequestAttribute(InteractionAuthInterceptor.USER_ID_ATTRIBUTE) Long userId,
            @RequestAttribute(InteractionAuthInterceptor.ROLE_ATTRIBUTE) String role) {
        service.deleteNote(userId, role, id);
        return ApiResponse.ok(null);
    }

    @PostMapping("/notes/{id}/collect")
    public ApiResponse<NoteView> collect(
            @PathVariable("id") Long id,
            @RequestAttribute(InteractionAuthInterceptor.USER_ID_ATTRIBUTE) Long userId,
            @RequestAttribute(InteractionAuthInterceptor.ROLE_ATTRIBUTE) String role) {
        return ApiResponse.ok(service.collect(userId, role, id));
    }
}