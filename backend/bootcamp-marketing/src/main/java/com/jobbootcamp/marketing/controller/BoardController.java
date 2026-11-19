package com.jobbootcamp.marketing.controller;

import com.jobbootcamp.common.web.ApiResponse;
import com.jobbootcamp.marketing.application.BoardService;
import com.jobbootcamp.marketing.config.MarketingAuthInterceptor;
import com.jobbootcamp.marketing.domain.dto.BoardResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/boards")
public class BoardController {

    private final BoardService boardService;

    public BoardController(BoardService boardService) {
        this.boardService = boardService;
    }

    @GetMapping("/monthly")
    public ApiResponse<BoardResponse> monthly(
            @RequestAttribute(MarketingAuthInterceptor.USER_ID_ATTRIBUTE) Long userId,
            @RequestParam(name = "month", required = false) String month,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "pageSize", defaultValue = "20") int pageSize) {
        return ApiResponse.ok(boardService.monthly(month, page, pageSize, userId));
    }
}