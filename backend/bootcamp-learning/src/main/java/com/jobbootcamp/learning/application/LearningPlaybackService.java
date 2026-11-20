package com.jobbootcamp.learning.application;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jobbootcamp.common.error.BusinessException;
import com.jobbootcamp.common.error.ErrorCode;
import com.jobbootcamp.contracts.internal.LessonMetadataResponse;
import com.jobbootcamp.contracts.internal.PlayAuthorizationResponse;
import com.jobbootcamp.contracts.internal.PlaySessionCheckResponse;
import com.jobbootcamp.learning.domain.CompletionEvent;
import com.jobbootcamp.learning.domain.LessonProgress;
import com.jobbootcamp.learning.domain.OutboxEvent;
import com.jobbootcamp.learning.domain.UserGuard;
import com.jobbootcamp.contracts.internal.AccessCheckResponse;
import com.jobbootcamp.learning.domain.dto.ProgressRequest;
import com.jobbootcamp.learning.domain.dto.ProgressResponse;
import com.jobbootcamp.learning.infrastructure.CompletionEventMapper;
import com.jobbootcamp.learning.infrastructure.LessonProgressMapper;
import com.jobbootcamp.learning.infrastructure.OutboxEventMapper;
import com.jobbootcamp.learning.infrastructure.UserGuardMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;

@Service
public class LearningPlaybackService {

    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");
    private static final String COMPLETION_EVENT = "learning.lesson-completed.v1";

    private final CourseClient courseClient;
    private final EntitlementService entitlementService;
    private final UserGuardMapper guardMapper;
    private final LessonProgressMapper progressMapper;
    private final CompletionEventMapper completionMapper;
    private final OutboxEventMapper outboxMapper;
    private final TransactionTemplate transactionTemplate;
    private final TransactionTemplate rowTemplate;

    public LearningPlaybackService(CourseClient courseClient,
                                   EntitlementService entitlementService,
                                   UserGuardMapper guardMapper,
                                   LessonProgressMapper progressMapper,
                                   CompletionEventMapper completionMapper,
                                   OutboxEventMapper outboxMapper,
                                   PlatformTransactionManager transactionManager) {
        this.courseClient = courseClient;
        this.entitlementService = entitlementService;
        this.guardMapper = guardMapper;
        this.progressMapper = progressMapper;
        this.completionMapper = completionMapper;
        this.outboxMapper = outboxMapper;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.rowTemplate = new TransactionTemplate(transactionManager);
        this.rowTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public PlayAuthorizationResponse authorize(long userId, long authSessionId, long lessonId) {
        LessonMetadataResponse metadata = requirePlayable(lessonId);
        AccessCheckResponse access = entitlementService.checkAccess(userId, metadata.courseId());
        if (!access.allowed()) {
            throw new BusinessException(ErrorCode.NO_COURSE_ACCESS, "没有该课程的有效学习权益");
        }
        return courseClient.issue(userId, authSessionId, lessonId);
    }

    public ProgressResponse updateProgress(long userId, long lessonId, ProgressRequest request) {
        LessonMetadataResponse metadata = requirePlayable(lessonId);
        PlaySessionCheckResponse session = courseClient.check(userId, request.playSessionId(), lessonId);
        if (!session.allowed() || !metadata.courseId().equals(session.courseId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "播放会话无效或已过期");
        }
        if (!metadata.mediaId().equals(session.mediaId())
                || (metadata.catalogVersion() != null
                && session.catalogVersion() != null
                && !metadata.catalogVersion().equals(session.catalogVersion()))) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "视频已更新，请重新获取播放授权");
        }
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        rowTemplate.executeWithoutResult(status -> guardMapper.insertIfAbsent(userId, now));
        return transactionTemplate.execute(status -> updateInTransaction(userId, metadata, session, request, now));
    }

    private ProgressResponse updateInTransaction(long userId, LessonMetadataResponse metadata,
                                                  PlaySessionCheckResponse session,
                                                  ProgressRequest request, LocalDateTime now) {
        UserGuard guard = guardMapper.selectForUpdate(userId);
        AccessCheckResponse access = entitlementService.checkAccess(userId, metadata.courseId());
        if (!access.allowed()) {
            throw new BusinessException(ErrorCode.NO_COURSE_ACCESS, "没有该课程的有效学习权益");
        }
        long duration = session.durationMs() != null
                ? session.durationMs()
                : metadata.durationMs() == null ? 0 : metadata.durationMs();
        if (duration <= 0) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "服务端未取得有效视频时长");
        }
        long position = Math.min(Math.max(0, request.positionMs()), duration);
        LessonProgress progress = progressMapper.selectOne(Wrappers.<LessonProgress>lambdaQuery()
                .eq(LessonProgress::getUserId, userId)
                .eq(LessonProgress::getLessonId, metadata.lessonId()));
        boolean wasCompleted = progress != null && Boolean.TRUE.equals(progress.getCompleted());
        boolean nowCompleted = wasCompleted || position * 2 > duration;
        LocalDateTime completedAt = progress == null ? null : progress.getCompletedAt();
        Long completionSequence = progress == null ? null : progress.getCompletionSequence();
        boolean newlyCompleted = !wasCompleted && nowCompleted;

        if (newlyCompleted) {
            long sequence = guard.getCompletionEventSequence() + 1;
            guardMapper.updateCompletionSequence(userId, sequence, now);
            completionSequence = sequence;
            completedAt = now;
            writeCompletion(userId, metadata, sequence, now);
        }

        if (progress == null) {
            progress = new LessonProgress();
            progress.setUserId(userId);
            progress.setCourseId(metadata.courseId());
            progress.setLessonId(metadata.lessonId());
            progress.setPositionMs(position);
            progress.setMaxPositionMs(position);
            progress.setCompleted(nowCompleted);
            progress.setCompletedAt(completedAt);
            progress.setCompletionSequence(completionSequence);
            progress.setCreatedAt(now);
            progress.setUpdatedAt(now);
            progressMapper.insert(progress);
        } else {
            progress.setCourseId(metadata.courseId());
            progress.setPositionMs(position);
            progress.setMaxPositionMs(Math.max(progress.getMaxPositionMs() == null ? 0 : progress.getMaxPositionMs(), position));
            progress.setCompleted(nowCompleted);
            progress.setCompletedAt(completedAt);
            progress.setCompletionSequence(completionSequence);
            progress.setUpdatedAt(now);
            progressMapper.updateById(progress);
        }

        long completedCount = 0;
        if (metadata.activeLessonIds() != null && !metadata.activeLessonIds().isEmpty()) {
            completedCount = progressMapper.selectCount(Wrappers.<LessonProgress>lambdaQuery()
                    .eq(LessonProgress::getUserId, userId)
                    .eq(LessonProgress::getCourseId, metadata.courseId())
                    .eq(LessonProgress::getCompleted, true)
                    .in(LessonProgress::getLessonId, metadata.activeLessonIds()));
        }
        long totalCount = metadata.totalCount() == null ? 0 : metadata.totalCount();
        return new ProgressResponse(nowCompleted, newlyCompleted, completedAt, completedCount, totalCount);
    }

    private void writeCompletion(long userId, LessonMetadataResponse metadata, long sequence, LocalDateTime now) {
        Instant occurredAt = now.toInstant(ZoneOffset.UTC);
        LocalDate businessDate = now.atZone(ZoneOffset.UTC).withZoneSameInstant(SHANGHAI).toLocalDate();
        String payload = "{\"userId\":\"" + userId
                + "\",\"courseId\":\"" + metadata.courseId()
                + "\",\"lessonId\":\"" + metadata.lessonId()
                + "\",\"completionEventSequence\":\"" + sequence
                + "\",\"occurredAt\":\"" + occurredAt + "\"}";

        CompletionEvent event = new CompletionEvent();
        event.setUserId(userId);
        event.setEventSequence(sequence);
        event.setCourseId(metadata.courseId());
        event.setLessonId(metadata.lessonId());
        event.setOccurredAt(now);
        event.setBusinessDate(businessDate);
        event.setPayload(payload);
        event.setCreatedAt(now);
        completionMapper.insert(event);

        OutboxEvent outbox = new OutboxEvent();
        outbox.setEventId(UUID.randomUUID().toString());
        outbox.setType(COMPLETION_EVENT);
        outbox.setAggregateId(Long.toString(userId));
        outbox.setAggregateVersion(sequence);
        outbox.setOccurredAt(now);
        outbox.setPayload(payload);
        outbox.setStatus("PENDING");
        outbox.setAttempt(0);
        outbox.setNextRetryAt(now);
        outbox.setCreatedAt(now);
        outbox.setUpdatedAt(now);
        outboxMapper.insert(outbox);
    }

    private LessonMetadataResponse requirePlayable(long lessonId) {
        LessonMetadataResponse metadata = courseClient.metadata(lessonId);
        if (metadata.mediaId() == null || metadata.durationMs() == null || metadata.durationMs() <= 0) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "该小节没有可播放的视频");
        }
        if ("DELETED".equals(metadata.status())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "小节已删除");
        }
        return metadata;
    }
}