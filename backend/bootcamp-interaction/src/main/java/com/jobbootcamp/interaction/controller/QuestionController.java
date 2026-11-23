package com.jobbootcamp.interaction.controller;

import com.jobbootcamp.common.web.ApiResponse;
import com.jobbootcamp.interaction.application.NoteService;
import com.jobbootcamp.interaction.application.QuestionService;
import com.jobbootcamp.interaction.config.InteractionAuthInterceptor;
import com.jobbootcamp.interaction.config.ModerationInterceptor;
import com.jobbootcamp.interaction.domain.dto.AnswerView;
import com.jobbootcamp.interaction.domain.dto.BodyRequest;
import com.jobbootcamp.interaction.domain.dto.CommentView;
import com.jobbootcamp.interaction.domain.dto.HideRequest;
import com.jobbootcamp.interaction.domain.dto.PageResponse;
import com.jobbootcamp.interaction.domain.dto.QuestionRequest;
import com.jobbootcamp.interaction.domain.dto.QuestionSummary;
import com.jobbootcamp.interaction.domain.dto.QuestionView;
import com.jobbootcamp.interaction.domain.dto.ThreadView;
import com.jobbootcamp.interaction.domain.dto.TraceAuthorView;
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

import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class QuestionController {

    private final QuestionService service;
    private final NoteService noteService;

    public QuestionController(QuestionService service, NoteService noteService) {
        this.service = service;
        this.noteService = noteService;
    }

    @GetMapping("/courses/{courseId}/questions")
    public ApiResponse<PageResponse<QuestionSummary>> list(
            @PathVariable("courseId") Long courseId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestAttribute(InteractionAuthInterceptor.USER_ID_ATTRIBUTE) Long userId,
            @RequestAttribute(InteractionAuthInterceptor.ROLE_ATTRIBUTE) String role) {
        return ApiResponse.ok(service.listQuestions(userId, role, courseId, page, pageSize));
    }

    @PostMapping("/courses/{courseId}/questions")
    public ApiResponse<QuestionView> create(
            @PathVariable("courseId") Long courseId,
            @RequestBody QuestionRequest request,
            @RequestAttribute(InteractionAuthInterceptor.USER_ID_ATTRIBUTE) Long userId,
            @RequestAttribute(InteractionAuthInterceptor.ROLE_ATTRIBUTE) String role) {
        return ApiResponse.ok(service.createQuestion(userId, role, courseId, request));
    }

    @GetMapping("/questions/{id}")
    public ApiResponse<ThreadView> thread(
            @PathVariable("id") Long id,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestAttribute(InteractionAuthInterceptor.USER_ID_ATTRIBUTE) Long userId,
            @RequestAttribute(InteractionAuthInterceptor.ROLE_ATTRIBUTE) String role) {
        return ApiResponse.ok(service.getQuestion(userId, role, id, page, pageSize));
    }

    @PatchMapping("/questions/{id}")
    public ApiResponse<QuestionView> update(
            @PathVariable("id") Long id,
            @RequestBody QuestionRequest request,
            @RequestAttribute(InteractionAuthInterceptor.USER_ID_ATTRIBUTE) Long userId,
            @RequestAttribute(InteractionAuthInterceptor.ROLE_ATTRIBUTE) String role) {
        return ApiResponse.ok(service.updateQuestion(userId, role, id, request));
    }

    @DeleteMapping("/questions/{id}")
    public ApiResponse<Void> delete(
            @PathVariable("id") Long id,
            @RequestAttribute(InteractionAuthInterceptor.USER_ID_ATTRIBUTE) Long userId,
            @RequestAttribute(InteractionAuthInterceptor.ROLE_ATTRIBUTE) String role) {
        service.deleteQuestion(userId, role, id);
        return ApiResponse.ok(null);
    }

    @GetMapping("/questions/{id}/answers")
    public ApiResponse<PageResponse<AnswerView>> answers(
            @PathVariable("id") Long id,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestAttribute(InteractionAuthInterceptor.USER_ID_ATTRIBUTE) Long userId,
            @RequestAttribute(InteractionAuthInterceptor.ROLE_ATTRIBUTE) String role) {
        return ApiResponse.ok(service.listAnswers(userId, role, id, page, pageSize));
    }

    @PostMapping("/questions/{id}/answers")
    public ApiResponse<AnswerView> answer(
            @PathVariable("id") Long id,
            @RequestBody BodyRequest request,
            @RequestAttribute(InteractionAuthInterceptor.USER_ID_ATTRIBUTE) Long userId,
            @RequestAttribute(InteractionAuthInterceptor.ROLE_ATTRIBUTE) String role) {
        return ApiResponse.ok(service.createAnswer(userId, role, id, request));
    }

    @GetMapping("/answers/{id}/comments")
    public ApiResponse<PageResponse<CommentView>> comments(
            @PathVariable("id") Long id,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestAttribute(InteractionAuthInterceptor.USER_ID_ATTRIBUTE) Long userId,
            @RequestAttribute(InteractionAuthInterceptor.ROLE_ATTRIBUTE) String role) {
        return ApiResponse.ok(service.listComments(userId, role, id, page, pageSize));
    }

    @PostMapping("/answers/{id}/comments")
    public ApiResponse<CommentView> comment(
            @PathVariable("id") Long id,
            @RequestBody BodyRequest request,
            @RequestAttribute(InteractionAuthInterceptor.USER_ID_ATTRIBUTE) Long userId,
            @RequestAttribute(InteractionAuthInterceptor.ROLE_ATTRIBUTE) String role) {
        return ApiResponse.ok(service.createComment(userId, role, id, request));
    }

    @PatchMapping("/answers/{id}")
    public ApiResponse<AnswerView> updateAnswer(
            @PathVariable("id") Long id,
            @RequestBody BodyRequest request,
            @RequestAttribute(InteractionAuthInterceptor.USER_ID_ATTRIBUTE) Long userId,
            @RequestAttribute(InteractionAuthInterceptor.ROLE_ATTRIBUTE) String role) {
        return ApiResponse.ok(service.updateAnswer(userId, role, id, request));
    }

    @DeleteMapping("/answers/{id}")
    public ApiResponse<Void> deleteAnswer(
            @PathVariable("id") Long id,
            @RequestAttribute(InteractionAuthInterceptor.USER_ID_ATTRIBUTE) Long userId,
            @RequestAttribute(InteractionAuthInterceptor.ROLE_ATTRIBUTE) String role) {
        service.deleteAnswer(userId, role, id);
        return ApiResponse.ok(null);
    }

    @PatchMapping("/comments/{id}")
    public ApiResponse<CommentView> updateComment(
            @PathVariable("id") Long id,
            @RequestBody BodyRequest request,
            @RequestAttribute(InteractionAuthInterceptor.USER_ID_ATTRIBUTE) Long userId,
            @RequestAttribute(InteractionAuthInterceptor.ROLE_ATTRIBUTE) String role) {
        return ApiResponse.ok(service.updateComment(userId, role, id, request));
    }

    @DeleteMapping("/comments/{id}")
    public ApiResponse<Void> deleteComment(
            @PathVariable("id") Long id,
            @RequestAttribute(InteractionAuthInterceptor.USER_ID_ATTRIBUTE) Long userId,
            @RequestAttribute(InteractionAuthInterceptor.ROLE_ATTRIBUTE) String role) {
        service.deleteComment(userId, role, id);
        return ApiResponse.ok(null);
    }

    @PostMapping("/admin/content/{type}/{id}/hide")
    public ApiResponse<Map<String, String>> hide(
            @PathVariable("type") String type,
            @PathVariable("id") Long id,
            @RequestBody HideRequest request,
            @RequestAttribute(ModerationInterceptor.ADMIN_ID_ATTRIBUTE) Long adminId) {
        String reason = request == null ? null : request.reason();
        if ("NOTE".equalsIgnoreCase(type)) {
            noteService.hideNote(adminId, id, reason);
        } else {
            service.hide(adminId, type, id, reason);
        }
        return ApiResponse.ok(Map.of("status", "hidden"));
    }

    @GetMapping("/admin/questions/{id}/author")
    public ApiResponse<TraceAuthorView> trace(
            @PathVariable("id") Long id,
            @RequestParam String reason,
            @RequestAttribute(ModerationInterceptor.ADMIN_ID_ATTRIBUTE) Long adminId) {
        return ApiResponse.ok(service.traceQuestionAuthor(adminId, id, reason));
    }
}