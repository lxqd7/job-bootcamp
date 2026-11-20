package com.jobbootcamp.learning.controller;

import com.jobbootcamp.common.error.BusinessException;
import com.jobbootcamp.common.error.ErrorCode;
import com.jobbootcamp.contracts.internal.CompletionEventItem;
import com.jobbootcamp.contracts.internal.CompletionEventsRequest;
import com.jobbootcamp.contracts.internal.CompletionEventsResponse;
import com.jobbootcamp.learning.domain.CompletionEvent;
import com.jobbootcamp.learning.infrastructure.CompletionEventMapper;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.ZoneOffset;
import java.util.List;

@RestController
@RequestMapping("/int/v1/completion-events")
public class InternalCompletionEventController {

    private final CompletionEventMapper completionMapper;

    public InternalCompletionEventController(CompletionEventMapper completionMapper) {
        this.completionMapper = completionMapper;
    }

    @PostMapping
    public CompletionEventsResponse events(@RequestBody CompletionEventsRequest request) {
        if (request.userId() == null || request.userId() <= 0) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "userId 不合法");
        }
        long after = request.afterSequence() == null ? 0 : Math.max(0, request.afterSequence());
        int limit = request.limit() == null ? 20 : Math.min(100, Math.max(1, request.limit()));
        List<CompletionEventItem> items = completionMapper.selectAfter(request.userId(), after, limit)
                .stream()
                .map(this::toItem)
                .toList();
        return new CompletionEventsResponse(items);
    }

    private CompletionEventItem toItem(CompletionEvent event) {
        return new CompletionEventItem(String.valueOf(event.getUserId()), String.valueOf(event.getCourseId()),
                String.valueOf(event.getLessonId()), event.getEventSequence(),
                event.getOccurredAt().toInstant(ZoneOffset.UTC));
    }
}