package com.jobbootcamp.learning.application;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobbootcamp.common.error.BusinessException;
import com.jobbootcamp.common.error.ErrorCode;
import com.jobbootcamp.contracts.internal.RefundBasisResponse;
import com.jobbootcamp.contracts.internal.RefundQualificationRequest;
import com.jobbootcamp.contracts.internal.RefundQualificationResponse;
import com.jobbootcamp.learning.domain.EntitlementSource;
import com.jobbootcamp.learning.domain.LessonProgress;
import com.jobbootcamp.learning.domain.OutboxEvent;
import com.jobbootcamp.learning.domain.RefundIntent;
import com.jobbootcamp.learning.domain.RefundIntentLesson;
import com.jobbootcamp.learning.domain.UserGuard;
import com.jobbootcamp.learning.domain.dto.RefundIntentView;
import com.jobbootcamp.learning.infrastructure.EntitlementSourceMapper;
import com.jobbootcamp.learning.infrastructure.LessonProgressMapper;
import com.jobbootcamp.learning.infrastructure.OutboxEventMapper;
import com.jobbootcamp.learning.infrastructure.RefundIntentLessonMapper;
import com.jobbootcamp.learning.infrastructure.RefundIntentMapper;
import com.jobbootcamp.learning.infrastructure.UserGuardMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 退款申请在学习侧的线性化点：锁 user_guard、分配 sequence、保存固定小节清单和完成数、
 * 冻结本订单来源；远程资格检查在本地事务提交后进行，失败时恢复来源并终止申请。
 */
@Service
public class RefundIntentService {

    private static final String REFUND_REQUEST_EVENT = "learning.refund-requested.v1";
    private static final ObjectMapper JSON = new ObjectMapper();

    private final RefundIntentMapper intentMapper;
    private final RefundIntentLessonMapper lessonMapper;
    private final EntitlementSourceMapper sourceMapper;
    private final UserGuardMapper guardMapper;
    private final LessonProgressMapper progressMapper;
    private final OutboxEventMapper outboxMapper;
    private final TradeRefundClient tradeClient;
    private final TransactionTemplate transactionTemplate;
    private final TransactionTemplate rowTemplate;

    public RefundIntentService(RefundIntentMapper intentMapper,
                               RefundIntentLessonMapper lessonMapper,
                               EntitlementSourceMapper sourceMapper,
                               UserGuardMapper guardMapper,
                               LessonProgressMapper progressMapper,
                               OutboxEventMapper outboxMapper,
                               TradeRefundClient tradeClient,
                               PlatformTransactionManager transactionManager) {
        this.intentMapper = intentMapper;
        this.lessonMapper = lessonMapper;
        this.sourceMapper = sourceMapper;
        this.guardMapper = guardMapper;
        this.progressMapper = progressMapper;
        this.outboxMapper = outboxMapper;
        this.tradeClient = tradeClient;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.rowTemplate = new TransactionTemplate(transactionManager);
        this.rowTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public RefundIntentView apply(Long userId, Long orderId, String reason) {
        RefundIntent active = intentMapper.selectActive(userId, orderId);
        if (active != null) {
            return view(active);
        }
        RefundBasisResponse basis = tradeClient.basis(orderId, userId);
        validateBasis(basis);
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        rowTemplate.executeWithoutResult(status -> guardMapper.insertIfAbsent(userId, now));
        RefundIntent created;
        try {
            created = transactionTemplate.execute(status ->
                    createIntent(userId, orderId, basis, reason, now));
        } catch (DuplicateKeyException duplicate) {
            RefundIntent raced = intentMapper.selectActive(userId, orderId);
            if (raced != null) {
                return view(raced);
            }
            throw duplicate;
        }
        if (created == null) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "退款申请未产生结果");
        }
        try {
            return qualify(created);
        } catch (RuntimeException e) {
            // 受理事实已提交；资格服务暂时不可用时返回 ACCEPTED，由后续退款流程恢复重试。
            return view(created);
        }
    }

    public RefundIntentView get(Long userId, String requestId) {
        RefundIntent intent = intentMapper.selectOne(Wrappers.<RefundIntent>lambdaQuery()
                .eq(RefundIntent::getRequestId, requestId));
        if (intent == null || !intent.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "退款申请不存在");
        }
        return view(intent);
    }

    private RefundIntent createIntent(Long userId, Long orderId, RefundBasisResponse basis,
                                      String reason, LocalDateTime now) {
        UserGuard guard = guardMapper.selectForUpdate(userId);
        RefundIntent active = intentMapper.selectActive(userId, orderId);
        if (active != null) {
            return active;
        }
        EntitlementSource source = sourceMapper.selectOne(Wrappers.<EntitlementSource>lambdaQuery()
                .eq(EntitlementSource::getUserId, userId)
                .eq(EntitlementSource::getOrderId, orderId)
                .in(EntitlementSource::getType, List.of("COURSE_ORDER", "ROUTE_ORDER")));
        if (source == null) {
            throw new BusinessException(ErrorCode.ORDER_SYNCING, "支付权益尚未就绪");
        }
        if (!"ACTIVE".equals(source.getState())) {
            throw new BusinessException(ErrorCode.CONFLICT, "该订单权益已在退款流程中");
        }

        long sequence = guard.getSequence() + 1;
        guardMapper.updateSequence(userId, sequence, now);
        RefundIntent intent = new RefundIntent();
        intent.setRequestId(UUID.randomUUID().toString());
        intent.setOrderId(orderId);
        intent.setUserId(userId);
        intent.setAcceptedAt(now);
        intent.setUserSequence(sequence);
        intent.setPaidAt(LocalDateTime.ofInstant(Instant.parse(basis.paidAt()), ZoneOffset.UTC));
        intent.setSnapshotHash(basis.snapshotHash());
        intent.setProductType(basis.productType());
        intent.setSourceId(source.getId());
        intent.setState("ACCEPTED");
        intent.setActiveOrderId(orderId);
        intent.setCreatedAt(now);
        intent.setUpdatedAt(now);

        int completed = 0;
        Set<Long> seen = new HashSet<>();
        List<RefundIntentLesson> lessons = new ArrayList<>();
        for (RefundBasisResponse.CourseLessons course : basis.courses()) {
            for (Long lessonId : course.lessonIds()) {
                if (!seen.add(lessonId)) {
                    continue;
                }
                LessonProgress progress = progressMapper.selectOne(Wrappers.<LessonProgress>lambdaQuery()
                        .eq(LessonProgress::getUserId, userId)
                        .eq(LessonProgress::getLessonId, lessonId));
                boolean done = progress != null && Boolean.TRUE.equals(progress.getCompleted());
                if (done) {
                    completed++;
                }
                RefundIntentLesson lesson = new RefundIntentLesson();
                lesson.setCourseId(course.courseId());
                lesson.setLessonId(lessonId);
                lesson.setCompleted(done);
                lesson.setCompletionSequence(progress == null ? null : progress.getCompletionSequence());
                lesson.setCreatedAt(now);
                lessons.add(lesson);
            }
        }
        if (seen.isEmpty()) {
            throw new BusinessException(ErrorCode.DATA_INTEGRITY, "退款清单为空");
        }
        intent.setCompletedCount(completed);
        intent.setTotalCount(seen.size());
        intent.setSourceVersion(source.getLifecycleVersion() + 1);
        intentMapper.insert(intent);
        for (RefundIntentLesson lesson : lessons) {
            lesson.setIntentId(intent.getId());
            lessonMapper.insert(lesson);
        }

        source.setState("FROZEN");
        source.setLifecycleVersion(source.getLifecycleVersion() + 1);
        source.setUpdatedAt(now);
        sourceMapper.updateById(source);
        writeRefundRequested(intent, reason, now);
        return intent;
    }

    private RefundIntentView qualify(RefundIntent intent) {
        RefundQualificationResponse result = tradeClient.qualify(new RefundQualificationRequest(
                intent.getId(), intent.getOrderId(), intent.getUserId(), intent.getSnapshotHash(),
                intent.getAcceptedAt().toInstant(ZoneOffset.UTC).toString(),
                intent.getCompletedCount(), intent.getTotalCount()));
        if (result == null) {
            return view(intent);
        }
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        if ("QUALIFIED".equals(result.state())) {
            transactionTemplate.executeWithoutResult(status -> {
                intentMapper.updateState(intent.getId(), "PROCESSING", null, intent.getOrderId(), now);
                intent.setState("PROCESSING");
                intent.setUpdatedAt(now);
            });
        } else if ("REJECTED".equals(result.state())) {
            restoreAndReject(intent, result.reason(), now);
        } else {
            transactionTemplate.executeWithoutResult(status -> {
                intentMapper.updateState(intent.getId(), "PROCESSING", result.reason(), intent.getOrderId(), now);
                intent.setState("PROCESSING");
                intent.setReason(result.reason());
                intent.setUpdatedAt(now);
            });
        }
        return view(intent);
    }

    private void restoreAndReject(RefundIntent intent, String reason, LocalDateTime now) {
        transactionTemplate.executeWithoutResult(status -> {
            guardMapper.selectForUpdate(intent.getUserId());
            EntitlementSource source = sourceMapper.selectById(intent.getSourceId());
            if (source != null && "FROZEN".equals(source.getState())) {
                source.setState("ACTIVE");
                source.setLifecycleVersion(source.getLifecycleVersion() + 1);
                source.setUpdatedAt(now);
                sourceMapper.updateById(source);
            }
            intentMapper.updateState(intent.getId(), "REJECTED", reason, null, now);
            intent.setState("REJECTED");
            intent.setReason(reason);
            intent.setActiveOrderId(null);
            intent.setUpdatedAt(now);
        });
    }

    private void writeRefundRequested(RefundIntent intent, String reason, LocalDateTime now) {
        try {
            String payload = JSON.writeValueAsString(new RefundRequestedPayload(
                    intent.getId(), intent.getOrderId(), intent.getUserId(),
                    intent.getAcceptedAt().toInstant(ZoneOffset.UTC).toString(),
                    intent.getUserSequence(), intent.getSnapshotHash(), intent.getCompletedCount(),
                    intent.getTotalCount(), intent.getSourceVersion(), reason));
            OutboxEvent event = new OutboxEvent();
            event.setEventId(UUID.randomUUID().toString());
            event.setType(REFUND_REQUEST_EVENT);
            event.setAggregateId(String.valueOf(intent.getId()));
            event.setAggregateVersion(intent.getUserSequence());
            event.setOccurredAt(now);
            event.setPayload(payload);
            event.setStatus("PENDING");
            event.setAttempt(0);
            event.setNextRetryAt(now);
            event.setCreatedAt(now);
            event.setUpdatedAt(now);
            outboxMapper.insert(event);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "退款申请事件序列化失败");
        }
    }

    private void validateBasis(RefundBasisResponse basis) {
        if (basis == null || basis.courses() == null || basis.courses().isEmpty()
                || basis.paidAt() == null || basis.snapshotHash() == null) {
            throw new BusinessException(ErrorCode.DATA_INTEGRITY, "交易退款基准不完整");
        }
        for (RefundBasisResponse.CourseLessons course : basis.courses()) {
            if (course.lessonIds() == null || course.lessonIds().isEmpty()) {
                throw new BusinessException(ErrorCode.DATA_INTEGRITY, "退款基准缺少小节");
            }
        }
    }

    private RefundIntentView view(RefundIntent intent) {
        return new RefundIntentView(intent.getRequestId(), intent.getOrderId(), intent.getAcceptedAt(),
                intent.getUserSequence(), intent.getState(), intent.getReason(),
                intent.getCompletedCount(), intent.getTotalCount());
    }

    private record RefundRequestedPayload(Long intentId, Long orderId, Long userId, String acceptedAt,
                                          Long sequence, String snapshotHash, int completedCount,
                                          int totalCount, Long sourceVersion, String reason) {
    }
}