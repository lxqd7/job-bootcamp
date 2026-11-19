package com.jobbootcamp.marketing.controller;

import com.jobbootcamp.common.error.BusinessException;
import com.jobbootcamp.common.error.ErrorCode;
import com.jobbootcamp.common.web.ApiResponse;
import com.jobbootcamp.common.web.TraceContext;
import com.jobbootcamp.contracts.event.EventEnvelope;
import com.jobbootcamp.marketing.application.BoardService;
import com.jobbootcamp.marketing.application.InboxProcessor;
import com.jobbootcamp.marketing.application.OutboxRelay;
import com.jobbootcamp.marketing.application.PointsService;
import com.jobbootcamp.marketing.domain.dto.DebtFixtureRequest;
import com.jobbootcamp.marketing.domain.dto.MonthScoreFixtureRequest;
import com.jobbootcamp.marketing.domain.dto.PointsAwardResult;
import com.jobbootcamp.marketing.domain.dto.SignDaysFixtureRequest;
import com.jobbootcamp.marketing.domain.dto.SignFixtureRequest;
import com.jobbootcamp.marketing.domain.dto.SignResult;
import com.jobbootcamp.marketing.domain.dto.TestAccountState;
import com.jobbootcamp.marketing.domain.dto.TestRewardRequest;
import com.jobbootcamp.marketing.domain.dto.TestRewardResponse;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 开发/验收专用。默认关闭，生产环境不注册；不对外提供“任意加积分”能力。
 */
@RestController
@RequestMapping("/dev")
@ConditionalOnProperty(name = "app.points.test-api-enabled", havingValue = "true")
public class DevPointsController {

    private static final String CONSUMER = "marketing.points.lesson-completed";

    private final PointsService pointsService;
    private final InboxProcessor inboxProcessor;
    private final OutboxRelay outboxRelay;
    private final BoardService boardService;

    public DevPointsController(PointsService pointsService,
                               InboxProcessor inboxProcessor,
                               OutboxRelay outboxRelay,
                               BoardService boardService) {
        this.pointsService = pointsService;
        this.inboxProcessor = inboxProcessor;
        this.outboxRelay = outboxRelay;
        this.boardService = boardService;
    }

    @PostMapping("/points/rewards")
    public ApiResponse<TestRewardResponse> reward(@Valid @RequestBody TestRewardRequest request) {
        try {
            UUID.fromString(request.eventId());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "eventId 必须是 UUID");
        }
        String businessKey = request.userId() + ":" + request.lessonId();
        EventEnvelope event = new EventEnvelope(
                request.eventId(),
                "learning.lesson-completed.v1",
                1,
                Long.toString(request.userId()),
                request.lessonId(),
                request.occurredAt(),
                TraceContext.currentTraceId(),
                payload(request, businessKey));
        AtomicReference<PointsAwardResult> awarded = new AtomicReference<>();
        boolean executed = inboxProcessor.process(CONSUMER, event, businessKey,
                ignored -> awarded.set(pointsService.awardVideo(
                        request.userId(), request.lessonId(), request.points(), request.occurredAt())));
        PointsAwardResult result = awarded.get();
        if (result == null) {
            var inboxRow = inboxProcessor.find(CONSUMER, request.eventId());
            if (inboxRow == null || !businessKey.equals(inboxRow.getBusinessKey())) {
                throw new BusinessException(ErrorCode.CONFLICT, "eventId 已用于其他业务事件");
            }
            result = pointsService.findVideoResult(request.userId(), request.lessonId());
            if (result == null) {
                throw new BusinessException(ErrorCode.CONFLICT, "事件已处理但没有对应积分流水");
            }
        }
        return ApiResponse.ok(new TestRewardResponse(!executed, result.ledgerId(), result.duplicate(),
                result.rewardPoints(), result.available(), result.debt(), result.monthScore(),
                result.businessDate().toString(), result.businessMonth(), result.resultCode()));
    }

    @GetMapping("/points/fixtures/accounts/{userId}")
    public ApiResponse<TestAccountState> accountState(@PathVariable("userId") Long userId) {
        return ApiResponse.ok(pointsService.testState(userId));
    }

    @PostMapping("/points/fixtures/debt")
    public ApiResponse<String> debtFixture(@Valid @RequestBody DebtFixtureRequest request) {
        pointsService.setDebtForTest(request.userId(), request.debt());
        return ApiResponse.ok("OK");
    }

    @PostMapping("/points/fixtures/sign")
    public ApiResponse<SignResult> signFixture(@Valid @RequestBody SignFixtureRequest request) {
        return ApiResponse.ok(pointsService.signIn(request.userId()));
    }

    @PostMapping("/points/fixtures/sign-days")
    public ApiResponse<String> signDaysFixture(@Valid @RequestBody SignDaysFixtureRequest request) {
        pointsService.seedSignDaysForTest(request.userId(), request.days(), request.endDate());
        return ApiResponse.ok("OK");
    }

    @PostMapping("/points/fixtures/month-score")
    public ApiResponse<String> monthScoreFixture(@Valid @RequestBody MonthScoreFixtureRequest request) {
        pointsService.setMonthScoreForTest(request.userId(), request.month(), request.score());
        return ApiResponse.ok("OK");
    }

    @PostMapping("/signs/{userId}/cache/rebuild")
    public ApiResponse<String> rebuildSignCache(@PathVariable("userId") Long userId,
                                                @RequestParam("month") String month) {
        pointsService.rebuildSignCacheForTest(userId, month);
        return ApiResponse.ok("OK");
    }

    @PostMapping("/boards/{month}/rebuild")
    public ApiResponse<String> rebuildBoard(@PathVariable("month") String month) {
        return ApiResponse.ok(boardService.rebuild(month) ? "OK" : "SKIPPED");
    }

    @PostMapping("/outbox/relay")
    public ApiResponse<OutboxRelay.RelayResult> relay(
            @RequestParam(name = "limit", defaultValue = "20") int limit) {
        return ApiResponse.ok(outboxRelay.relayOnce(limit));
    }

    private String payload(TestRewardRequest request, String businessKey) {
        return "{\"userId\":\"" + request.userId()
                + "\",\"lessonId\":\"" + request.lessonId()
                + "\",\"points\":\"" + request.points()
                + "\",\"occurredAt\":\"" + request.occurredAt()
                + "\",\"businessKey\":\"" + businessKey + "\"}";
    }
}