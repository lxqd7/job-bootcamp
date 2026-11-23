package com.jobbootcamp.interaction.controller;

import com.jobbootcamp.common.web.ApiResponse;
import com.jobbootcamp.interaction.application.NoteService;
import com.jobbootcamp.interaction.config.InteractionAuthInterceptor;
import com.jobbootcamp.interaction.domain.dto.ExcellentRequest;
import com.jobbootcamp.interaction.domain.dto.NoteView;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/teacher/notes")
public class TeacherNoteController {

    private final NoteService service;

    public TeacherNoteController(NoteService service) {
        this.service = service;
    }

    @PutMapping("/{id}/excellent")
    public ApiResponse<NoteView> evaluate(
            @PathVariable("id") Long id,
            @RequestBody ExcellentRequest request,
            @RequestAttribute(InteractionAuthInterceptor.USER_ID_ATTRIBUTE) Long userId,
            @RequestAttribute(InteractionAuthInterceptor.ROLE_ATTRIBUTE) String role) {
        return ApiResponse.ok(service.evaluate(userId, role, id,
                request == null ? null : request.contentRevision()));
    }

    @DeleteMapping("/{id}/excellent")
    public ApiResponse<NoteView> cancel(
            @PathVariable("id") Long id,
            @RequestAttribute(InteractionAuthInterceptor.USER_ID_ATTRIBUTE) Long userId,
            @RequestAttribute(InteractionAuthInterceptor.ROLE_ATTRIBUTE) String role) {
        return ApiResponse.ok(service.cancelEvaluated(userId, role, id));
    }
}